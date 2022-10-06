package no.nav.klage.oppgave.clients.kabaldocument

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.oppgave.clients.kabaldocument.model.response.BrevmottakerWithJoarkAndDokDistInfo
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.DokumentMetadata
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import java.util.*

@Service
class KabalDocumentGateway(
    private val kabalDocumentClient: KabalDocumentClient,
    private val kabalDocumentMapper: KabalDocumentMapper
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun createKomplettDokumentEnhet(
        behandling: Behandling,
        hovedDokument: DokumentUnderArbeid,
        vedlegg: SortedSet<DokumentUnderArbeid>
    ): UUID {
        return UUID.fromString(
            kabalDocumentClient.createDokumentEnhetWithDokumentreferanser(
                kabalDocumentMapper.mapBehandlingToDokumentEnhetWithDokumentreferanser(
                    behandling,
                    hovedDokument,
                    vedlegg
                )
            ).id
        )
    }

    fun getMetadataOmHovedDokument(dokumentEnhetId: UUID): DokumentMetadata? {
        val dokumentEnhet = kabalDocumentClient.getDokumentEnhet(dokumentEnhetId)

        return dokumentEnhet.hovedDokument?.let {
            DokumentMetadata(
                title = it.name,
                size = it.size,
                opplastet = it.opplastet
            )
        }
    }

    fun fullfoerDokumentEnhet(dokumentEnhetId: UUID): List<BrevmottakerWithJoarkAndDokDistInfo> =
        kabalDocumentClient.fullfoerDokumentEnhet(dokumentEnhetId).brevMottakerWithJoarkAndDokDistInfoList

    fun isHovedDokumentUploaded(dokumentEnhetId: UUID): Boolean {
        return getMetadataOmHovedDokument(dokumentEnhetId) != null
    }

}