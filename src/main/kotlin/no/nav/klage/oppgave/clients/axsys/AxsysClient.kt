package no.nav.klage.oppgave.clients.axsys

import brave.Tracer
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.SAKSBEHANDLERE_I_ENHET_CACHE
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.TILGANGER_CACHE
import no.nav.klage.oppgave.domain.klage.KLAGEENHET_PREFIX
import no.nav.klage.oppgave.service.TokenService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class AxsysClient(
    private val axsysWebClient: WebClient,
    private val tokenService: TokenService,
    private val tracer: Tracer
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        const val IT_ENHET = "2990"
    }

    @Value("\${spring.application.name}")
    lateinit var applicationName: String

    @Retryable
    @Cacheable(TILGANGER_CACHE)
    fun getTilgangerForSaksbehandler(navIdent: String): Tilganger {
        logger.debug("Fetching tilganger for saksbehandler with Nav-Ident {}", navIdent)

        return try {
            val tilganger = axsysWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/tilgang/{navIdent}")
                        .queryParam("inkluderAlleEnheter", "true")
                        .build(navIdent)
                }
                .header("Authorization", "Bearer ${tokenService.getSaksbehandlerAccessTokenWithAxsysScope()}")
                .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
                .header("Nav-Consumer-Id", applicationName)

                .retrieve()
                .bodyToMono<Tilganger>()
                .block() ?: throw RuntimeException("Tilganger could not be fetched")

            Tilganger(
                enheter = tilganger.enheter.filter { enhet ->
                    enhet.enhetId.startsWith(KLAGEENHET_PREFIX) ||
                            enhet.enhetId == IT_ENHET
                }
            )
            tilganger
        } catch (notFound: WebClientResponseException.NotFound) {
            logger.warn("Got a 404 fetching tilganger for saksbehandler {}, returning empty object", navIdent)
            //TODO: Burde det smelle hardt her isf å returnere tomt objekt?
            Tilganger(emptyList())
        }
    }

    @Retryable
    @Cacheable(SAKSBEHANDLERE_I_ENHET_CACHE)
    fun getSaksbehandlereIEnhet(enhetId: String): List<Bruker> {
        logger.debug("Fetching brukere in enhet {}", enhetId)

        return axsysWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/enhet/{enhetId}/brukere")
                    .build(enhetId)
            }.header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .header("Nav-Consumer-Id", applicationName)

            .retrieve()
            .bodyToMono<List<Bruker>>()
            .block() ?: throw RuntimeException("Brukere in enhet could not be fetched")
    }
}



