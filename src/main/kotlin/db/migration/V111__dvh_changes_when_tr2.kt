package db.migration

import no.nav.klage.oppgave.domain.kafka.BehandlingState
import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.util.*


class V111__dvh_changes_when_tr2: BaseJavaMigration() {
    override fun migrate(context: Context) {
        val preparedStatement = context.connection.prepareStatement(
            """
                update klage.kafka_event
                    set json_payload = ?
                    where id = ?
            """.trimIndent()
        )

        context.connection.createStatement().use { select ->
            select.executeQuery(
                """
                    select ke.id, ke.json_payload
                    from klage.kafka_event ke
                    where ke.type = 'STATS_DVH'
                    and ke.json_payload like '%MOTTATT_FRA_TR%'
                    """)
                .use { rows ->
                while (rows.next()) {
                    val kafkaEventId = rows.getObject(1, UUID::class.java)
                    val jsonPayload = rows.getString(2)

                    val statistikkTilDVH = ourJacksonObjectMapper().readValue(jsonPayload, StatistikkTilDVH::class.java)

                    val modifiedVersion = statistikkTilDVH.copy(
                        behandlingStatus = BehandlingState.AVSLUTTET,
                    )

                    preparedStatement.setString(1, ourJacksonObjectMapper().writeValueAsString(modifiedVersion))
                    preparedStatement.setObject(2, kafkaEventId)

                    preparedStatement.executeUpdate()
                }
            }
        }
    }
}