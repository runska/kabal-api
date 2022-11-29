package no.nav.klage.oppgave.clients.kabalinnstillinger

import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Medunderskrivere
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.MedunderskrivereInput
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.SaksbehandlerSearchInput
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Saksbehandlere
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.*

@Component
class KabalInnstillingerClient(
    private val kabalInnstillingerWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun searchMedunderskrivere(input: MedunderskrivereInput): Medunderskrivere {
        logger.debug("Searching medunderskrivere in kabal-innstillinger")
        return kabalInnstillingerWebClient.post()
            .uri { it.path("/search/medunderskrivere").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getUserAccessTokenWithKabalInnstillingerScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(input)
            .retrieve()
            .bodyToMono<Medunderskrivere>()
            .block() ?: throw RuntimeException("Could not search medunderskrivere")
    }

    fun searchSaksbehandlere(input: SaksbehandlerSearchInput): Saksbehandlere {
        logger.debug("Searching saksbehandlere in kabal-innstillinger")
        return kabalInnstillingerWebClient.post()
            .uri { it.path("/search/saksbehandlere").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getUserAccessTokenWithKabalInnstillingerScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(input)
            .retrieve()
            .bodyToMono<Saksbehandlere>()
            .block() ?: throw RuntimeException("Could not search saksbehandlere")
    }
}