package no.nav.klage.oppgave.clients.pdl

data class PersonGraphqlQuery(
    val query: String,
    val variables: Variables
)

data class Variables(
    val identer: List<String>
)

fun hentPersonQuery(fnrList: List<String>): PersonGraphqlQuery {
    val query = PersonGraphqlQuery::class.java.getResource("/pdl/hentPerson.graphql").readText().replace("[\n\r]", "")
    return PersonGraphqlQuery(query, Variables(fnrList))
}
