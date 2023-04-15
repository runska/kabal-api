package no.nav.klage.oppgave.api.view

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateAnkeBasedOnKabinInput(
    val klagebehandlingId: UUID,
    val mottattNav: LocalDate,
    val fristInWeeks: Int,
    val klager: OversendtPartId?,
    val fullmektig: OversendtPartId?,
    val ankeDocumentJournalpostId: String,
) {
    data class OversendtPartId(
        val type: OversendtPartIdType,
        val value: String
    )

    enum class OversendtPartIdType { PERSON, VIRKSOMHET }
}

data class CreatedAnkeResponse(
    val mottakId: UUID,
)