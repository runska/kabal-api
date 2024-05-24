package no.nav.klage.dokument.service

import no.nav.klage.dokument.clients.clamav.ClamAvClient
import no.nav.klage.dokument.exceptions.AttachmentIsEmptyException
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class MellomlagretDokumentValidatorService(
    private val clamAvClient: ClamAvClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun validateAttachment(file: MultipartFile) {
        logger.debug("Validating attachment.")
        if (file.isEmpty) {
            logger.warn("Attachment is empty")
            throw AttachmentIsEmptyException()
        }

//        if (file.hasVirus()) {
//            logger.warn("Attachment has virus")
//            throw AttachmentHasVirusException()
//        }

        logger.debug("Validation successful.")
    }

    private fun MultipartFile.hasVirus() = !clamAvClient.scan(this.resource)
}