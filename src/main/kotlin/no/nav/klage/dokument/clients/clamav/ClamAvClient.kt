package no.nav.klage.dokument.clients.clamav

import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class ClamAvClient(private val clamAvWebClient: WebClient) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun hasVirus(file: MultipartFile): Boolean {
        logger.debug("Scanning document")

        val bodyBuilder = MultipartBodyBuilder()
        bodyBuilder.part("file", file.resource).filename(file.name)

        val response = try {
            clamAvWebClient.post()
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono<List<ScanResult>>()
                .block()
        } catch (ex: Throwable) {
            logger.warn("Error from clamAV", ex)
            listOf(ScanResult("Unknown", ClamAvResult.ERROR))
        }

        if (response == null) {
            logger.warn("No response from virus scan.")
            return false
        }

        if (response.size != 1) {
            logger.warn("Wrong size response from virus scan.")
            return false
        }

        val (_, result) = response[0]
        logger.debug("${file.name} ${result.name}")
        return when (result) {
            ClamAvResult.OK -> false
            ClamAvResult.FOUND -> {
                logger.warn("${file.name} has virus")
                true
            }
            ClamAvResult.ERROR -> {
                logger.warn("Error from virus scan for file ${file.name}")
                throw RuntimeException("Error from virus scan for file ${file.name}")
            }
        }
    }
}