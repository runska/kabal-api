package no.nav.klage.oppgave.clients.kaka

import no.nav.klage.kodeverk.Enhet
import no.nav.klage.oppgave.clients.kaka.model.request.SaksdataInput
import no.nav.klage.oppgave.clients.kaka.model.response.KakaOutput
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.exceptions.InvalidProperty
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import java.util.*

@Service
class KakaApiGateway(private val kakaApiClient: KakaApiClient) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun createKvalitetsvurdering(kvalitetsvurderingVersion: Int): KakaOutput {
        val kakaOutput = kakaApiClient.createKvalitetsvurdering(kvalitetsvurderingVersion = kvalitetsvurderingVersion)
        logger.debug("New kvalitetsvurderingId {} created in Kaka", kakaOutput)
        return kakaOutput
    }

    fun finalizeBehandling(behandling: Behandling) {
        logger.debug("Sending saksdata to Kaka because behandling is finished.")
        kakaApiClient.finalizeBehandling(
            saksdataInput = behandling.toSaksdataInput(),
            kvalitetsvurderingVersion = behandling.kakaKvalitetsvurderingVersion
        )
    }

    fun deleteKvalitetsvurderingV1(kvalitetsvurderingId: UUID) {
        logger.debug("Deleting kvalitetsvurdering with id $kvalitetsvurderingId in Kaka.")
        kakaApiClient.deleteKvalitetsvurderingV1(
            kvalitetsvurderingId = kvalitetsvurderingId
        )
    }

    fun getValidationErrors(behandling: Behandling): List<InvalidProperty> {
        logger.debug("Getting kvalitetsvurdering validation errors")
        return kakaApiClient.getValidationErrors(
            kvalitetsvurderingId = behandling.kakaKvalitetsvurderingId!!,
            ytelseId = behandling.ytelse.id,
            typeId = behandling.type.id,
            kvalitetsvurderingVersion = behandling.kakaKvalitetsvurderingVersion,
        ).validationErrors.map {
            InvalidProperty(
                field = it.field,
                reason = it.reason
            )
        }
    }

    private fun Behandling.toSaksdataInput(): SaksdataInput {
        val vedtaksinstansEnhet =
            when (this) {
                is Klagebehandling -> {
                    if (Enhet.values().none { it.navn == avsenderEnhetFoersteinstans }) {
                        logger.error("avsenderEnhetFoersteinstans $avsenderEnhetFoersteinstans not found in internal kodeverk")
                    }
                    avsenderEnhetFoersteinstans
                }

                is Ankebehandling -> {
                    if (Enhet.values().none { it.navn == klageBehandlendeEnhet }) {
                        logger.error("klageBehandlendeEnhet $klageBehandlendeEnhet not found in internal kodeverk")
                    }
                    klageBehandlendeEnhet
                }

                else -> {
                    throw RuntimeException("Wrong behandling type")
                }
            }

        val tilknyttetEnhet = Enhet.values().find { it.navn == tildeling?.enhet!! }

        return SaksdataInput(
            sakenGjelder = sakenGjelder.partId.value,
            sakstype = type.id,
            ytelseId = ytelse.id,
            mottattKlageinstans = mottattKlageinstans.toLocalDate(),
            vedtaksinstansEnhet = vedtaksinstansEnhet,
            mottattVedtaksinstans = if (this is Klagebehandling) mottattVedtaksinstans else null,
            utfall = currentDelbehandling().utfall!!.id,
            registreringshjemler = currentDelbehandling().hjemler.map { it.id },
            kvalitetsvurderingId = kakaKvalitetsvurderingId!!,
            avsluttetAvSaksbehandler = currentDelbehandling().avsluttetAvSaksbehandler!!,
            utfoerendeSaksbehandler = tildeling?.saksbehandlerident!!,
            tilknyttetEnhet = tilknyttetEnhet!!.navn
        )
    }
}

