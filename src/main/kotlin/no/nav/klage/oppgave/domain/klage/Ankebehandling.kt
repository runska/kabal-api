package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.MedunderskriverFlyt
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.klage.Ankebehandling.Status.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity
@DiscriminatorValue("anke")
class Ankebehandling(
    @Column(name = "klage_vedtaks_dato")
    val klageVedtaksDato: LocalDate? = null,
    @Column(name = "klage_behandlende_enhet")
    val klageBehandlendeEnhet: String,
    //Fins i noen tilfeller, men ikke alle.
    @Column(name = "klage_id")
    var klagebehandlingId: UUID? = null,

//    Finn ut hvordan dette skal fungere i anker etter hvert
//    @Column(name = "dato_behandling_avsluttet_av_saksbehandler")
//    var avsluttetAvSaksbehandler: LocalDateTime? = null,


    //Common properties between klage/anke
    id: UUID = UUID.randomUUID(),
    klager: Klager,
    sakenGjelder: SakenGjelder,
    ytelse: Ytelse,
    type: Type,
    kildeReferanse: String,
    dvhReferanse: String? = null,
    sakFagsystem: Fagsystem? = null,
    sakFagsakId: String? = null,
    //Umulig å vite innsendt-dato.
    innsendt: LocalDate? = null,
    //Settes automatisk i klage, må kunne justeres i anke. Bør også representeres i delbehandlinger. Må gjøres entydig i anke, hører antageligvis ikke hjemme i felles klasse.
    mottattKlageinstans: LocalDateTime,
    //TODO: Trenger denne være nullable? Den blir da alltid satt i createKlagebehandlingFromMottak?
    //Litt usikkert om dette hører mest hjemme her eller på delbehandlinger.
    frist: LocalDate? = null,
    //Hører hjemme på delbehandlinger, men her er det mer usikkerhet enn for medunderskriver. Litt om pragmatikken, bør se hva som er enklest å få til.
    tildeling: Tildeling? = null,
    //Hører hjemme på delbehandlinger, men her er det mer usikkerhet enn for medunderskriver
    tildelingHistorikk: MutableSet<TildelingHistorikk> = mutableSetOf(),
    //Hovedbehandling
    mottakId: UUID,
    kakaKvalitetsvurderingId: UUID? = null,
    created: LocalDateTime = LocalDateTime.now(),
    modified: LocalDateTime = LocalDateTime.now(),
    //Kommer fra innsending
    kildesystem: Fagsystem,
    delbehandlinger: Set<Delbehandling>,
    saksdokumenter: MutableSet<Saksdokument> = mutableSetOf(),
    hjemler: MutableSet<Hjemmel> = mutableSetOf(),
) : Behandling(
    id = id,
    klager = klager,
    sakenGjelder = sakenGjelder,
    ytelse = ytelse,
    type = type,
    kildeReferanse = kildeReferanse,
    mottattKlageinstans = mottattKlageinstans,
    mottakId = mottakId,
    kildesystem = kildesystem,
    modified = modified,
    created = created,
    kakaKvalitetsvurderingId = kakaKvalitetsvurderingId,
    tildelingHistorikk = tildelingHistorikk,
    tildeling = tildeling,
    frist = frist,
    innsendt = innsendt,
    sakFagsakId = sakFagsakId,
    sakFagsystem = sakFagsystem,
    dvhReferanse = dvhReferanse,
    delbehandlinger = delbehandlinger,
    saksdokumenter = saksdokumenter,
    hjemler = hjemler,
)  {
    override fun toString(): String {
        return "Ankebehandling(id=$id, " +
                "modified=$modified, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Ankebehandling

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    /**
     * Brukes til ES og statistikk per nå
     */
    fun getStatus(): Status {
        return when {
            currentDelbehandling().avsluttet != null -> Status.FULLFOERT
            currentDelbehandling().avsluttetAvSaksbehandler != null -> Status.AVSLUTTET_AV_SAKSBEHANDLER
            currentDelbehandling().medunderskriverFlyt == MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER -> Status.SENDT_TIL_MEDUNDERSKRIVER
            currentDelbehandling().medunderskriverFlyt == MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER -> RETURNERT_TIL_SAKSBEHANDLER
            currentDelbehandling().medunderskriver?.saksbehandlerident != null -> MEDUNDERSKRIVER_VALGT
            tildeling?.saksbehandlerident != null -> TILDELT
            tildeling?.saksbehandlerident == null -> IKKE_TILDELT
            else -> UKJENT
        }
    }

    enum class Status {
        IKKE_TILDELT, TILDELT, MEDUNDERSKRIVER_VALGT, SENDT_TIL_MEDUNDERSKRIVER, RETURNERT_TIL_SAKSBEHANDLER, AVSLUTTET_AV_SAKSBEHANDLER, FULLFOERT, UKJENT
    }
}