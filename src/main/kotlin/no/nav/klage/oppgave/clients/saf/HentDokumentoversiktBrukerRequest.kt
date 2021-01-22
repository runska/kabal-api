package no.nav.klage.oppgave.clients.saf

data class HentDokumentoversiktBrukerGraphqlQuery(
    val query: String,
    val variables: Variables
)

data class Variables(val brukerId: BrukerId, val foerste: Int, val etter: String?)
data class BrukerId(val id: String, val type: BrukerIdType = BrukerIdType.FNR)
enum class BrukerIdType { FNR }


fun hentDokumentoversiktBrukerQuery(
    fnr: String,
    pageSize: Int,
    previousPageRef: String?
): HentDokumentoversiktBrukerGraphqlQuery {
    val query =
        HentDokumentoversiktBrukerGraphqlQuery::class.java.getResource("/saf/hentDokumentoversiktBruker.graphql")
            .readText().replace("[\n\r]", "")
    return HentDokumentoversiktBrukerGraphqlQuery(query, Variables(BrukerId(fnr), pageSize, previousPageRef))
}