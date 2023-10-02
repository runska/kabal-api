package no.nav.klage.dokument.domain.dokumenterunderarbeid

import jakarta.persistence.*
import no.nav.klage.oppgave.domain.klage.BehandlingRole
import java.time.LocalDateTime
import java.util.*


@Entity
@DiscriminatorValue("smartdokument_vedlegg")
class SmartdokumentUnderArbeidAsVedlegg(
    @Column(name = "size")
    override var size: Long?,
    @Column(name = "smarteditor_id")
    override val smartEditorId: UUID,
    @Column(name = "smarteditor_template_id")
    override var smartEditorTemplateId: String,
    @Column(name = "mellomlager_id")
    override var mellomlagerId: String?,

    //Common properties
    id: UUID = UUID.randomUUID(),
    name: String,
    behandlingId: UUID,
    created: LocalDateTime,
    modified: LocalDateTime,
    markertFerdig: LocalDateTime?,
    markertFerdigBy: String?,
    ferdigstilt: LocalDateTime?,
    parentId: UUID,
    creatorIdent: String,
    creatorRole: BehandlingRole,
) : DokumentUnderArbeidAsSmartdokument, DokumentUnderArbeidAsVedlegg(
    id = id,
    name = name,
    behandlingId = behandlingId,
    created = created,
    modified = modified,
    markertFerdig = markertFerdig,
    markertFerdigBy = markertFerdigBy,
    ferdigstilt = ferdigstilt,
    parentId = parentId,
    creatorIdent = creatorIdent,
    creatorRole = creatorRole,
)