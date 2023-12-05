package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.FlowState
import no.nav.klage.kodeverk.FradelingReason
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object BehandlingSetters {

    fun Behandling.setTildeling(
        nyVerdiSaksbehandlerident: String?,
        nyVerdiEnhet: String?,
        fradelingReason: FradelingReason?,
        utfoerendeIdent: String,
        fradelingWithChangedHjemmelIdList: String?,
    ): BehandlingEndretEvent {
        if (!(nyVerdiSaksbehandlerident == null && nyVerdiEnhet == null) &&
            !(nyVerdiSaksbehandlerident != null && nyVerdiEnhet != null)
        ) {
            error("saksbehandler and enhet must both be set (or null)")
        }

        val gammelVerdiSaksbehandlerident = tildeling?.saksbehandlerident
        val gammelVerdiEnhet = tildeling?.enhet
        val gammelVerdiTidspunkt = tildeling?.tidspunkt
        val tidspunkt = LocalDateTime.now()

        //record initial state
        if (tildelingHistorikk.isEmpty()) {
            recordTildelingHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
                fradelingReason = null,
                hjemmelIdList = hjemler.joinToString(",") { it.id },
            )
        }

        tildeling = if (nyVerdiSaksbehandlerident == null) {
            null
        } else {
            Tildeling(nyVerdiSaksbehandlerident, nyVerdiEnhet, tidspunkt)
        }
        modified = tidspunkt

        recordTildelingHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = utfoerendeIdent,
            fradelingReason = fradelingReason,
            hjemmelIdList = if (tildeling == null) {
                fradelingWithChangedHjemmelIdList
            } else hjemler.joinToString(",") { it.id },
        )

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.TILDELT_TIDSPUNKT,
            fraVerdi = gammelVerdiTidspunkt?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            tilVerdi = tidspunkt.format(DateTimeFormatter.ISO_LOCAL_DATE),
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.TILDELT_SAKSBEHANDLERIDENT,
            fraVerdi = gammelVerdiSaksbehandlerident,
            tilVerdi = nyVerdiSaksbehandlerident,
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.TILDELT_ENHET,
            fraVerdi = gammelVerdiEnhet,
            tilVerdi = nyVerdiEnhet,
            tidspunkt = tidspunkt
        )
            ?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    private fun Behandling.recordTildelingHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
        fradelingReason: FradelingReason?,
        hjemmelIdList: String?,
    ) {
        tildelingHistorikk.add(
            TildelingHistorikk(
                saksbehandlerident = tildeling?.saksbehandlerident,
                enhet = tildeling?.enhet,
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
                fradelingReason = fradelingReason,
                hjemmelIdList = hjemmelIdList,
            )
        )
    }

    fun Behandling.setMedunderskriverFlowState(
        nyMedunderskriverFlowState: FlowState,
        utfoerendeIdent: String,
    ): BehandlingEndretEvent {
        val gammelVerdiMedunderskriverFlowState = medunderskriverFlowState
        val tidspunkt = LocalDateTime.now()

        //record initial history
        if (medunderskriverHistorikk.isEmpty()) {
            recordMedunderskriverHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
            )
        }

        medunderskriverFlowState = nyMedunderskriverFlowState
        modified = tidspunkt

        recordMedunderskriverHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = utfoerendeIdent
        )

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.MEDUNDERSKRIVER_FLOW_STATE_ID,
            fraVerdi = gammelVerdiMedunderskriverFlowState.id,
            tilVerdi = nyMedunderskriverFlowState.id,
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setMedunderskriverNavIdent(
        nyMedunderskriverNavIdent: String?,
        utfoerendeIdent: String
    ): BehandlingEndretEvent {
        val gammelVerdiMedunderskriverNavIdent = medunderskriver?.saksbehandlerident
        val tidspunkt = LocalDateTime.now()

        //record initial history
        if (medunderskriverHistorikk.isEmpty()) {
            recordMedunderskriverHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
            )
        }

        medunderskriver = MedunderskriverTildeling(
            saksbehandlerident = nyMedunderskriverNavIdent,
            tidspunkt = tidspunkt,
        )

        if (medunderskriverFlowState == FlowState.RETURNED || nyMedunderskriverNavIdent == null) {
            medunderskriverFlowState = FlowState.NOT_SENT
        }

        recordMedunderskriverHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = utfoerendeIdent
        )

        modified = tidspunkt

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.MEDUNDERSKRIVERIDENT,
            fraVerdi = gammelVerdiMedunderskriverNavIdent,
            tilVerdi = nyMedunderskriverNavIdent,
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    private fun Behandling.recordMedunderskriverHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
    ) {
        medunderskriverHistorikk.add(
            MedunderskriverHistorikk(
                saksbehandlerident = medunderskriver?.saksbehandlerident,
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
                flowState = medunderskriverFlowState,
            )
        )
    }

    fun Behandling.setROLFlowState(
        newROLFlowStateState: FlowState,
        utfoerendeIdent: String,
    ): BehandlingEndretEvent {
        val oldValue = rolFlowState
        val now = LocalDateTime.now()

        //record initial state
        if (rolHistorikk.isEmpty()) {
            recordRolHistory(
                tidspunkt = created,
                utfoerendeIdent = null
            )
        }

        rolFlowState = newROLFlowStateState
        modified = now

        recordRolHistory(
            tidspunkt = now,
            utfoerendeIdent = utfoerendeIdent
        )

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.ROL_FLOW_STATE_ID,
            fraVerdi = oldValue.id,
            tilVerdi = rolFlowState.id,
            tidspunkt = now
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setROLReturnedDate(
        setNull: Boolean,
        utfoerendeIdent: String
    ): BehandlingEndretEvent {
        val oldValue = rolReturnedDate
        val now = LocalDateTime.now()

        //record initial state
        if (rolHistorikk.isEmpty()) {
            recordRolHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
            )
        }

        rolReturnedDate = if (setNull) null else now
        modified = now

        recordRolHistory(
            tidspunkt = created,
            utfoerendeIdent = utfoerendeIdent,
        )

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.ROL_RETURNED_TIDSPUNKT,
            fraVerdi = oldValue.toString(),
            tilVerdi = rolReturnedDate.toString(),
            tidspunkt = now
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    fun Behandling.setROLIdent(
        newROLIdent: String?,
        utfoerendeIdent: String
    ): BehandlingEndretEvent {
        val oldValue = rolIdent
        val now = LocalDateTime.now()

        //record initial state
        if (rolHistorikk.isEmpty()) {
            recordRolHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
            )
        }

        rolIdent = newROLIdent
        modified = now

        if (rolFlowState == FlowState.RETURNED) {
            rolFlowState = FlowState.NOT_SENT
        }

        recordRolHistory(
            tidspunkt = now,
            utfoerendeIdent = utfoerendeIdent
        )

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.ROL_IDENT,
            fraVerdi = oldValue,
            tilVerdi = rolIdent,
            tidspunkt = now
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    private fun Behandling.recordRolHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
    ) {
        rolHistorikk.add(
            RolHistorikk(
                rolIdent = rolIdent,
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
                flowState = rolFlowState,
            )
        )
    }

    fun Behandling.setSattPaaVent(
        nyVerdi: SattPaaVent?,
        utfoerendeIdent: String
    ): BehandlingEndretEvent {
        val gammelSattPaaVent = sattPaaVent
        val tidspunkt = LocalDateTime.now()

        //record initial state
        if (sattPaaVentHistorikk.isEmpty()) {
            recordSattPaaVentHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
            )
        }

        sattPaaVent = nyVerdi
        modified = tidspunkt

        recordSattPaaVentHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = utfoerendeIdent,
        )

        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        endringslogg(
            saksbehandlerident = utfoerendeIdent,
            felt = Felt.SATT_PAA_VENT,
            fraVerdi = gammelSattPaaVent.toString(),
            tilVerdi = nyVerdi.toString(),
            tidspunkt = tidspunkt
        )?.let { endringslogginnslag.add(it) }

        return BehandlingEndretEvent(behandling = this, endringslogginnslag = endringslogginnslag)
    }

    private fun Behandling.recordSattPaaVentHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
    ) {
        sattPaaVentHistorikk.add(
            SattPaaVentHistorikk(
                sattPaaVent = sattPaaVent,
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
            )
        )
    }

    fun Behandling.setMottattKlageinstans(
        nyVerdi: LocalDateTime,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = mottattKlageinstans
        val tidspunkt = LocalDateTime.now()
        mottattKlageinstans = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.MOTTATT_KLAGEINSTANS_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setFrist(
        nyVerdi: LocalDate,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = frist
        val tidspunkt = LocalDateTime.now()
        frist = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.FRIST_DATO,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setInnsendingshjemler(
        nyVerdi: Set<Hjemmel>,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = hjemler
        val tidspunkt = LocalDateTime.now()
        hjemler = nyVerdi
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.INNSENDINGSHJEMLER_ID_LIST,
                fraVerdi = gammelVerdi.joinToString { it.id },
                tilVerdi = nyVerdi.joinToString { it.id },
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setFullmektig(
        nyVerdi: PartId?,
        utfoerendeIdent: String
    ): BehandlingEndretEvent {
        val gammelVerdi = klager.prosessfullmektig
        val tidspunkt = LocalDateTime.now()

        //record initial state
        if (fullmektigHistorikk.isEmpty()) {
            recordFullmektigHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
            )
        }

        if (nyVerdi == null) {
            klager.prosessfullmektig = null
        } else {
            klager.prosessfullmektig = Prosessfullmektig(partId = nyVerdi, skalPartenMottaKopi = false)
        }
        modified = tidspunkt

        recordFullmektigHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = utfoerendeIdent,
        )

        val endringslogg =
            endringslogg(
                saksbehandlerident = utfoerendeIdent,
                felt = Felt.FULLMEKTIG,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = nyVerdi.toString(),
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    private fun Behandling.recordFullmektigHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
    ) {
        fullmektigHistorikk.add(
            FullmektigHistorikk(
                partId = klager.prosessfullmektig?.partId,
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
            )
        )
    }

    fun Behandling.setKlager(
        nyVerdi: PartId,
        utfoerendeIdent: String
    ): BehandlingEndretEvent {
        val gammelVerdi = klager
        val tidspunkt = LocalDateTime.now()

        //record initial state
        if (klagerHistorikk.isEmpty()) {
            recordKlagerHistory(
                tidspunkt = created,
                utfoerendeIdent = null,
            )
        }

        klager.partId = nyVerdi

        recordKlagerHistory(
            tidspunkt = tidspunkt,
            utfoerendeIdent = utfoerendeIdent,
        )

        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = utfoerendeIdent,
                felt = Felt.KLAGER,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = klager.toString(),
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    private fun Behandling.recordKlagerHistory(
        tidspunkt: LocalDateTime,
        utfoerendeIdent: String?,
    ) {
        klagerHistorikk.add(
            KlagerHistorikk(
                partId = klager.partId,
                tidspunkt = tidspunkt,
                utfoerendeIdent = utfoerendeIdent,
            )
        )
    }

    fun Behandling.setRegistreringshjemler(
        nyVerdi: Set<Registreringshjemmel>,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = registreringshjemler
        val tidspunkt = LocalDateTime.now()
        registreringshjemler = nyVerdi.toMutableSet()
        modified = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.REGISTRERINGSHJEMLER_ID_LIST,
                fraVerdi = gammelVerdi.joinToString { it.id },
                tilVerdi = nyVerdi.joinToString { it.id },
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setUtfall(
        nyVerdi: Utfall?,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = utfall
        val tidspunkt = LocalDateTime.now()
        utfall = nyVerdi
        modified = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.UTFALL_ID,
                fraVerdi = gammelVerdi?.id,
                tilVerdi = utfall?.id,
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setExtraUtfallSet(
        nyVerdi: Set<Utfall>,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = extraUtfallSet
        val tidspunkt = LocalDateTime.now()
        extraUtfallSet = nyVerdi
        modified = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.EXTRA_UTFALL_SET,
                fraVerdi = gammelVerdi.joinToString { it.id },
                tilVerdi = extraUtfallSet.joinToString { it.id },
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setAvsluttetAvSaksbehandler(
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = avsluttetAvSaksbehandler
        val tidspunkt = LocalDateTime.now()
        avsluttetAvSaksbehandler = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = tidspunkt.toString(),
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setAvsluttet(
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val gammelVerdi = avsluttet
        val tidspunkt = LocalDateTime.now()
        avsluttet = tidspunkt
        modified = tidspunkt
        val endringslogg =
            endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.AVSLUTTET_TIDSPUNKT,
                fraVerdi = gammelVerdi.toString(),
                tilVerdi = tidspunkt.toString(),
                tidspunkt = tidspunkt
            )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.addSaksdokument(
        saksdokument: Saksdokument,
        saksbehandlerident: String
    ): BehandlingEndretEvent? {
        if (saksdokumenter.none { it.journalpostId == saksdokument.journalpostId && it.dokumentInfoId == saksdokument.dokumentInfoId }) {
            val tidspunkt = LocalDateTime.now()
            saksdokumenter.add(saksdokument)
            modified = tidspunkt
            val endringslogg = Endringslogginnslag.endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.SAKSDOKUMENT,
                fraVerdi = null,
                tilVerdi = saksdokument.toString(),
                behandlingId = id,
                tidspunkt = tidspunkt
            )
            return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
        }
        return null
    }

    fun Behandling.addSaksdokumenter(
        saksdokumentList: List<Saksdokument>,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val existingSaksdokumenter = saksdokumenter.joinToString()
        val tidspunkt = LocalDateTime.now()
        saksdokumenter.addAll(saksdokumentList)
        modified = tidspunkt
        val endringslogg = Endringslogginnslag.endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.SAKSDOKUMENT,
            fraVerdi = existingSaksdokumenter,
            tilVerdi = saksdokumenter.joinToString(),
            behandlingId = id,
            tidspunkt = tidspunkt
        )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.removeSaksdokument(
        saksdokument: Saksdokument,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val tidspunkt = LocalDateTime.now()
        saksdokumenter.removeIf { it.id == saksdokument.id }
        modified = tidspunkt
        val endringslogg = Endringslogginnslag.endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.SAKSDOKUMENT,
            fraVerdi = saksdokument.toString(),
            tilVerdi = null,
            behandlingId = id,
            tidspunkt = tidspunkt
        )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    fun Behandling.setFeilregistrering(
        feilregistrering: Feilregistrering,
        saksbehandlerident: String
    ): BehandlingEndretEvent {
        val tidspunkt = LocalDateTime.now()
        modified = tidspunkt
        this.feilregistrering = feilregistrering
        val endringslogg = Endringslogginnslag.endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = Felt.FEILREGISTRERING,
            fraVerdi = null,
            tilVerdi = feilregistrering.toString(),
            behandlingId = id,
            tidspunkt = tidspunkt
        )
        return BehandlingEndretEvent(behandling = this, endringslogginnslag = listOfNotNull(endringslogg))
    }

    private fun Behandling.endringslogg(
        saksbehandlerident: String,
        felt: Felt,
        fraVerdi: String?,
        tilVerdi: String?,
        tidspunkt: LocalDateTime
    ): Endringslogginnslag? {
        return Endringslogginnslag.endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = felt,
            fraVerdi = fraVerdi,
            tilVerdi = tilVerdi,
            behandlingId = this.id,
            tidspunkt = tidspunkt
        )
    }

}