package no.nav.klage.oppgave.domain.klage

import no.nav.klage.oppgave.api.view.Fagsystem
import no.nav.klage.oppgave.api.view.FagsystemConverter
import no.nav.klage.oppgave.domain.kodeverk.Tema
import no.nav.klage.oppgave.domain.kodeverk.TemaConverter
import no.nav.klage.oppgave.domain.kodeverk.Type
import no.nav.klage.oppgave.domain.kodeverk.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "mottak", schema = "klage")
class Mottak(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Version
    @Column(name = "versjon")
    val versjon: Long = 1L,
    @Column(name = "tema_id")
    @Convert(converter = TemaConverter::class)
    var tema: Tema,
    @Column(name = "type_id")
    @Convert(converter = TypeConverter::class)
    var type: Type,
    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "klager_id", nullable = false)
    var klager: Klager,
    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "saken_gjelder_id", nullable = true)
    var sakenGjelder: SakenGjelder? = null,
    @Column(name = "sak_fagsystem")
    @Convert(converter = FagsystemConverter::class)
    var sakFagsystem: Fagsystem? = null,
    @Column(name = "sak_fagsak_id")
    var sakFagsakId: String? = null,
    @Column(name = "kilde_referanse")
    var kildeReferanse: String,
    @Column(name = "dvh_referanse")
    var dvhReferanse: String? = null,
    @Column(name = "innsyn_url")
    val innsynUrl: String? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "mottak_id", referencedColumnName = "id", nullable = false)
    var hjemmelListe: MutableSet<MottakHjemmel>? = null,
    @Column(name = "avsender_saksbehandlerident")
    var avsenderSaksbehandlerident: String? = null,
    @Column(name = "avsender_enhet")
    var avsenderEnhet: String? = null,
    @Column(name = "oversendt_klageinstans_enhet")
    var oversendtKaEnhet: String? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "mottak_id", referencedColumnName = "id", nullable = false)
    val mottakDokument: MutableSet<MottakDokument> = mutableSetOf(),
    @Column(name = "dato_innsendt")
    val innsendtDato: LocalDate? = null,
    @Column(name = "dato_mottatt_foersteinstans")
    val mottattNavDato: LocalDate? = null,
    @Column(name = "dato_oversendt_klageinstans")
    var oversendtKaDato: LocalDate,
    @Column(name = "dato_frist_fra_foersteinstans")
    var fristFraFoersteinstans: LocalDate? = null,
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "modified")
    val modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "kilde")
    val kilde: String,
    @Column(name = "kommentar")
    val kommentar: String? = null,
) {

    override fun toString(): String {
        return "Mottak(id=$id, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Mottak

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
