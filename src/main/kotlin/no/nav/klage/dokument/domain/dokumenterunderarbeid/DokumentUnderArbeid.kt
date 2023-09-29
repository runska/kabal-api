package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.*
import no.nav.klage.oppgave.domain.klage.BehandlingRole
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime
import java.util.*

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "dokument_under_arbeid", schema = "klage")
@DynamicUpdate
@DiscriminatorColumn(name = "dokument_under_arbeid_type")
open class DokumentUnderArbeid(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "mellomlager_id")
    open var mellomlagerId: String?,
    @Column(name = "name")
    open var name: String,
    @Column(name = "behandling_id")
    open var behandlingId: UUID,
    @Column(name = "created")
    open var created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "modified")
    open var modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "markert_ferdig")
    open var markertFerdig: LocalDateTime? = null,
    @Column(name = "markert_ferdig_by")
    open var markertFerdigBy: String? = null,
    @Column(name = "ferdigstilt")
    open var ferdigstilt: LocalDateTime? = null,
    @Column(name = "parent_id")
    open var parentId: UUID? = null,
    @Column(name = "creator_ident")
    open var creatorIdent: String,
    @Column(name = "creator_role")
    @Enumerated(EnumType.STRING)
    open var creatorRole: BehandlingRole,
) : Comparable<DokumentUnderArbeid> {

    override fun compareTo(other: DokumentUnderArbeid): Int =
        created.compareTo(other.created)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DokumentUnderArbeid

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "DokumentUnderArbeid(id=$id)"
    }

    fun erMarkertFerdig(): Boolean {
        return markertFerdig != null
    }

    fun erFerdigstilt(): Boolean {
        return ferdigstilt != null
    }

    fun ferdigstillHvisIkkeAlleredeFerdigstilt(tidspunkt: LocalDateTime) {
        if (ferdigstilt == null) {
            ferdigstilt = tidspunkt
            modified = tidspunkt
        }
    }

    fun markerFerdigHvisIkkeAlleredeMarkertFerdig(tidspunkt: LocalDateTime, saksbehandlerIdent: String) {
        if (markertFerdig == null) {
            markertFerdig = tidspunkt
            markertFerdigBy = saksbehandlerIdent
            modified = tidspunkt
        }
    }

    enum class DokumentUnderArbeidType {
        UPLOADED,
        SMART,
        JOURNALFOERT
    }
}