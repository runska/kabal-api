package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.klage.MottakDokumentType
import no.nav.klage.oppgave.exceptions.BehandlingNotFoundException
import no.nav.klage.oppgave.service.*
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*


@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/api/internal")
class KabinApiController(
    private val klagebehandlingService: KlagebehandlingService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val mottakService: MottakService,
    private val ankebehandlingService: AnkebehandlingService,
    private val dokumentService: DokumentService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/completedklagebehandlinger")
    fun getCompletedKlagebehandlinger(
        @RequestBody input: GetCompletedKlagebehandlingerInput
    ): List<CompletedKlagebehandling> {
        logMethodDetails(
            methodName = ::getCompletedKlagebehandlinger.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return klagebehandlingService.findCompletedKlagebehandlingerByPartIdValue(partIdValue = input.idnummer)
    }

    @GetMapping("/completedklagebehandlinger/{klagebehandlingId}")
    fun getCompletedKlagebehandling(
        @PathVariable klagebehandlingId: UUID
    ): CompletedKlagebehandling {
        logMethodDetails(
            methodName = ::getCompletedKlagebehandling.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        return klagebehandlingService.findCompletedKlagebehandlingById(klagebehandlingId)
    }

    @PostMapping("/createanke")
    fun createAnke(
        @RequestBody input: CreateAnkeBasedOnKabinInput
    ) {
        logMethodDetails(
            methodName = ::createAnke.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        mottakService.createAnkeMottakFromKabinInput(input = input)

        //TODO: Sjekk behov for å sende Kafka-melding om ANKE_OPPRETTET, dobbeltsjekk DVH
    }

    @GetMapping("/anker/{mottakId}/status")
    fun getCreatedAnkebehandlingStatus(
        @PathVariable mottakId: UUID
    ): CreatedBehandlingStatusForKabin {
        logMethodDetails(
            methodName = ::getCreatedAnkebehandlingStatus.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger
        )

        val mottak =
            mottakService.getMottak(mottakId = mottakId) ?: throw RuntimeException("mottak not found for id $mottakId")
        val ankebehandling = ankebehandlingService.getAnkebehandlingFromMottakId(mottakId)
            ?: throw BehandlingNotFoundException("anke not found")

        val completedKlagebehandling = klagebehandlingService.findCompletedKlagebehandlingById(ankebehandling.klagebehandlingId!!)

        return CreatedBehandlingStatusForKabin(
            typeId = Type.ANKE.id,
            behandlingId = completedKlagebehandling.behandlingId,
            ytelseId = completedKlagebehandling.ytelseId,
            utfallId = completedKlagebehandling.utfallId,
            vedtakDate = completedKlagebehandling.vedtakDate,
            sakenGjelder = completedKlagebehandling.sakenGjelder,
            klager = completedKlagebehandling.klager,
            fullmektig = completedKlagebehandling.fullmektig,
            tilknyttedeDokumenter = completedKlagebehandling.tilknyttedeDokumenter,
            mottattNav = ankebehandling.mottattKlageinstans.toLocalDate(),
            frist = ankebehandling.frist!!,
            sakFagsakId = completedKlagebehandling.sakFagsakId,
            sakFagsystem = completedKlagebehandling.sakFagsystem,
            journalpost = dokumentService.getDokumentReferanse(
                journalpostId = mottak.mottakDokument.find { it.type == MottakDokumentType.BRUKERS_ANKE }!!.journalpostId,
                behandling = ankebehandling
            )
        )
    }
}