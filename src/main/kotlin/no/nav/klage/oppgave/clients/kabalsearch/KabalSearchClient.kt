package no.nav.klage.oppgave.clients.kabalsearch

import brave.Tracer
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.*

@Component
class KabalSearchClient(
    private val kabalSearchWebClient: WebClient,
    private val tracer: Tracer
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun deleteBehandling(
        behandlingId: UUID
    ) {
        kabalSearchWebClient.delete()
            .uri { it.path("/internal/behandling/{id}").build(behandlingId.toString()) }
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<Void>()
            .block()
    }
}