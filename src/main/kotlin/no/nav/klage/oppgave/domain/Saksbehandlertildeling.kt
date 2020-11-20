package no.nav.klage.oppgave.domain

data class Saksbehandlertildeling(
    val navIdent: String,
    val oppgaveversjon: String
)

data class Saksbehandlerfjerning(
    val oppgaveversjon: String
)