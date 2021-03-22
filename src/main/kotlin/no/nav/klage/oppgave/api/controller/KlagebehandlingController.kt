package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import no.nav.klage.oppgave.api.facade.KlagebehandlingFacade
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.AuditLogEvent
import no.nav.klage.oppgave.domain.AuditLogEvent.Action.KLAGEBEHANDLING_VIEW
import no.nav.klage.oppgave.domain.AuditLogEvent.Decision.ALLOW
import no.nav.klage.oppgave.exceptions.BehandlingsidWrongFormatException
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.util.AuditLogger
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Api(tags = ["klage-oppgave-api"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
class KlagebehandlingController(
    private val klagebehandlingFacade: KlagebehandlingFacade,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val auditLogger: AuditLogger
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/klagebehandlinger/{id}")
    fun getKlagebehandling(
        @PathVariable("id") klagebehandlingId: String
    ): KlagebehandlingView {
        logger.debug(
            "getKlagebehandling is requested by ident {} for klagebehandling {}",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId
        )
        return klagebehandlingFacade.getKlagebehandling(klagebehandlingId.toUUIDOrException()).also {
            auditLogger.log(
                AuditLogEvent(
                    navIdent = innloggetSaksbehandlerRepository.getInnloggetIdent(),
                    action = KLAGEBEHANDLING_VIEW,
                    decision = ALLOW,
                    personFnr = it.foedselsnummer
                )
            )
        }
    }

    @PutMapping("/klagebehandlinger/{id}/sakstype")
    fun putSakstype(
        @PathVariable("id") klagebehandlingId: String,
        @RequestBody input: KlagebehandlingSakstypeInput
    ): KlagebehandlingView {
        logger.debug(
            "putSakstype is requested by ident {} for klagebehandling {}",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId
        )
        return klagebehandlingFacade.setSakstype(
            klagebehandlingId.toUUIDOrException(),
            input.sakstype,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
    }

    @PutMapping("/klagebehandlinger/{id}/tema")
    fun putTema(
        @PathVariable("id") klagebehandlingId: String,
        @RequestBody input: KlagebehandlingTemaInput
    ): KlagebehandlingView {
        logger.debug(
            "putTema is requested by ident {} for klagebehandling {}",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId
        )
        return klagebehandlingFacade.setTema(
            klagebehandlingId.toUUIDOrException(),
            input.tema,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
    }

    @PutMapping("/klagebehandlinger/{id}/innsendt")
    fun putInnsendt(
        @PathVariable("id") klagebehandlingId: String,
        @RequestBody input: KlagebehandlingInnsendtInput
    ): KlagebehandlingView {
        logger.debug(
            "putInnsendt is requested by ident {} for klagebehandling {}",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId
        )
        return klagebehandlingFacade.setInnsendt(
            klagebehandlingId.toUUIDOrException(),
            input.innsendt,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
    }

    @PutMapping("/klagebehandlinger/{id}/mottattfoersteinstans")
    fun putMottattFoersteinstans(
        @PathVariable("id") klagebehandlingId: String,
        @RequestBody input: KlagebehandlingMottattFoersteinstansInput
    ): KlagebehandlingView {
        logger.debug(
            "putMottattFoersteinstans is requested by ident {} for klagebehandling {}",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId
        )
        return klagebehandlingFacade.setMottattFoersteinstans(
            klagebehandlingId.toUUIDOrException(),
            input.mottattFoersteinstans,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
    }

    @PutMapping("/klagebehandlinger/{id}/mottattklageinstans")
    fun putMottattKlageinstans(
        @PathVariable("id") klagebehandlingId: String,
        @RequestBody input: KlagebehandlingMottattKlageinstansInput
    ): KlagebehandlingView {
        logger.debug(
            "putMottattKlageinstans is requested by ident {} for klagebehandling {}",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId
        )
        return klagebehandlingFacade.setMottattKlageinstans(
            klagebehandlingId.toUUIDOrException(),
            input.mottattKlageinstans,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
    }

    @PutMapping("/klagebehandlinger/{id}/frist")
    fun putFrist(
        @PathVariable("id") klagebehandlingId: String,
        @RequestBody input: KlagebehandlingFristInput
    ): KlagebehandlingView {
        logger.debug(
            "putFrist is requested by ident {} for klagebehandling {}",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId
        )
        return klagebehandlingFacade.setFrist(
            klagebehandlingId.toUUIDOrException(),
            input.frist,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
    }

    @PutMapping("/klagebehandlinger/{id}/avsendersaksbehandlerident")
    fun putAvsenderSaksbehandlerident(
        @PathVariable("id") klagebehandlingId: String,
        @RequestBody input: KlagebehandlingAvsenderSaksbehandleridentFoersteinstansInput
    ): KlagebehandlingView {
        logger.debug(
            "putAvsenderSaksbehandlerident is requested by ident {} for klagebehandling {}",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId
        )
        return klagebehandlingFacade.setAvsenderSaksbehandleridentFoersteinstans(
            klagebehandlingId.toUUIDOrException(),
            input.avsenderSaksbehandlerident,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
    }

    @PutMapping("/klagebehandlinger/{id}/avsenderenhet")
    fun putAvsenderEnhet(
        @PathVariable("id") klagebehandlingId: String,
        @RequestBody input: KlagebehandlingAvsenderEnhetFoersteinstansInput
    ): KlagebehandlingView {
        logger.debug(
            "putAvsenderEnhet is requested by ident {} for klagebehandling {}",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId
        )
        return klagebehandlingFacade.setAvsenderEnhetFoersteinstans(
            klagebehandlingId.toUUIDOrException(),
            input.avsenderEnhet,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
    }

    private fun String.toUUIDOrException() =
        try {
            UUID.fromString(this)
        } catch (e: Exception) {
            logger.error("KlagebehandlingId could not be parsed as an UUID", e)
            throw BehandlingsidWrongFormatException("KlagebehandlingId could not be parsed as an UUID")
        }
}