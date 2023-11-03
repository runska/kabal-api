package no.nav.klage.dokument.repositories

import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsHoveddokument
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional
interface SmartdokumentUnderArbeidAsHoveddokumentRepository :
    JpaRepository<SmartdokumentUnderArbeidAsHoveddokument, UUID> {

    fun findByBehandlingIdAndMarkertFerdigIsNull(behandlingId: UUID): Set<SmartdokumentUnderArbeidAsHoveddokument>

    fun findByMarkertFerdigNotNullAndFerdigstiltNull(): Set<SmartdokumentUnderArbeidAsHoveddokument>

    fun findByBehandlingIdAndDokarkivReferencesIsNotEmpty(behandlingId: UUID): Set<SmartdokumentUnderArbeidAsHoveddokument>
}