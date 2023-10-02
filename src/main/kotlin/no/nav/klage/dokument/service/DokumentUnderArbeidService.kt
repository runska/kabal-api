package no.nav.klage.dokument.service

import jakarta.transaction.Transactional
import no.nav.klage.dokument.api.view.DocumentValidationResponse
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.dokument.clients.kabaljsontopdf.KabalJsonToPdfClient
import no.nav.klage.dokument.clients.kabalsmarteditorapi.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.domain.FysiskDokument
import no.nav.klage.dokument.domain.PDFDocument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.*
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.exceptions.JsonToPdfValidationException
import no.nav.klage.dokument.repositories.*
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Template
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentMapper
import no.nav.klage.oppgave.clients.saf.graphql.Journalpost
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.events.DokumentFerdigstiltAvSaksbehandler
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.addSaksdokument
import no.nav.klage.oppgave.exceptions.InvalidProperty
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.exceptions.SectionedValidationErrorWithDetailsException
import no.nav.klage.oppgave.exceptions.ValidationSection
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.DokumentService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class DokumentUnderArbeidService(
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val opplastetDokumentUnderArbeidRepository: OpplastetDokumentUnderArbeidRepository,
    private val opplastetDokumentUnderArbeidAsVedleggRepository: OpplastetDokumentUnderArbeidAsVedleggRepository,
    private val smartDokumentUnderArbeidRepository: SmartdokumentUnderArbeidRepository,
    private val smartDokumentUnderArbeidAsVedleggRepository: SmartdokumentUnderArbeidAsVedleggRepository,
    private val journalfoertDokumentUnderArbeidRepository: JournalfoertDokumentUnderArbeidAsVedleggRepository,
    private val attachmentValidator: MellomlagretDokumentValidatorService,
    private val mellomlagerService: MellomlagerService,
    private val smartEditorApiGateway: DefaultKabalSmartEditorApiGateway,
    private val kabalJsonToPdfClient: KabalJsonToPdfClient,
    private val behandlingService: BehandlingService,
    private val kabalDocumentGateway: KabalDocumentGateway,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val safClient: SafGraphQlClient,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val dokumentService: DokumentService,
    private val kabalDocumentMapper: KabalDocumentMapper,
    private val eregClient: EregClient,
    private val innholdsfortegnelseService: InnholdsfortegnelseService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        const val SYSTEMBRUKER = "SYSTEMBRUKER"
    }

    fun opprettOgMellomlagreNyttHoveddokument(
        behandlingId: UUID,
        dokumentType: DokumentType,
        opplastetFil: FysiskDokument?,
        innloggetIdent: String,
        tittel: String,
        parentId: UUID?,
    ): DokumentUnderArbeid {
        //Sjekker lesetilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandling(behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        validateCanCreateDocuments(
            behandlingRole = behandlingRole,
            parentDocument = if (parentId != null) dokumentUnderArbeidRepository.getReferenceById(parentId) else null
        )

        if (opplastetFil == null) {
            throw DokumentValidationException("No file uploaded")
        }

        attachmentValidator.validateAttachment(opplastetFil)
        val mellomlagerId = mellomlagerService.uploadDocument(opplastetFil)

        val now = LocalDateTime.now()

        val document = if (parentId == null) {
            opplastetDokumentUnderArbeidRepository.save(
                OpplastetDokumentUnderArbeid(
                    mellomlagerId = mellomlagerId,
                    size = opplastetFil.content.size.toLong(),
                    name = tittel,
                    dokumentType = dokumentType,
                    behandlingId = behandlingId,
                    creatorIdent = innloggetIdent,
                    creatorRole = behandlingRole,
                    created = now,
                    modified = now,
                    markertFerdig = null,
                    markertFerdigBy = null,
                    ferdigstilt = null,
                )
            )
        } else {
            opplastetDokumentUnderArbeidAsVedleggRepository.save(
                OpplastetDokumentUnderArbeidAsVedlegg(
                    mellomlagerId = mellomlagerId,
                    size = opplastetFil.content.size.toLong(),
                    name = tittel,
                    behandlingId = behandlingId,
                    creatorIdent = innloggetIdent,
                    creatorRole = behandlingRole,
                    parentId = parentId,
                    created = now,
                    modified = now,
                    markertFerdig = null,
                    markertFerdigBy = null,
                    ferdigstilt = null,
                )
            )
        }
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_OPPLASTET,
            fraVerdi = null,
            tilVerdi = document.created.toString(),
            tidspunkt = document.created,
            dokumentId = document.id,
        )

        return document
    }

    fun opprettSmartdokument(
        behandlingId: UUID,
        dokumentType: DokumentType,
        json: String?,
        smartEditorTemplateId: String,
        innloggetIdent: String,
        tittel: String,
        parentId: UUID?,
    ): DokumentUnderArbeid {
        //Sjekker lesetilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandling(behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        validateCanCreateDocuments(
            behandlingRole = behandlingRole,
            parentDocument = if (parentId != null) dokumentUnderArbeidRepository.getReferenceById(parentId) else null
        )

        if (json == null) {
            throw DokumentValidationException("Ingen json angitt")
        }
        val smartEditorDocumentId =
            smartEditorApiGateway.createDocument(
                json = json,
                dokumentType = dokumentType,
                innloggetIdent = innloggetIdent,
                documentTitle = tittel
            )

        val now = LocalDateTime.now()

        val document = if (parentId == null) {
            smartDokumentUnderArbeidRepository.save(
                SmartdokumentUnderArbeid(
                    mellomlagerId = null,
                    size = null,
                    name = tittel,
                    dokumentType = dokumentType,
                    behandlingId = behandlingId,
                    smartEditorId = smartEditorDocumentId,
                    smartEditorTemplateId = smartEditorTemplateId,
                    creatorIdent = innloggetIdent,
                    creatorRole = behandlingRole,
                    created = now,
                    modified = now,
                    markertFerdig = null,
                    markertFerdigBy = null,
                    ferdigstilt = null,
                )
            )
        } else {
            smartDokumentUnderArbeidAsVedleggRepository.save(
                SmartdokumentUnderArbeidAsVedlegg(
                    mellomlagerId = null,
                    size = null,
                    name = tittel,
                    behandlingId = behandlingId,
                    smartEditorId = smartEditorDocumentId,
                    smartEditorTemplateId = smartEditorTemplateId,
                    creatorIdent = innloggetIdent,
                    creatorRole = behandlingRole,
                    parentId = parentId,
                    created = now,
                    modified = now,
                    markertFerdig = null,
                    markertFerdigBy = null,
                    ferdigstilt = null,
                )
            )
        }
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.SMARTDOKUMENT_OPPRETTET,
            fraVerdi = null,
            tilVerdi = document.created.toString(),
            tidspunkt = document.created,
            dokumentId = document.id,
        )

        return document
    }

    fun createJournalfoerteDokumenter(
        parentId: UUID,
        journalfoerteDokumenter: Set<JournalfoertDokumentReference>,
        behandlingId: UUID,
        innloggetIdent: String,
    ): Pair<List<DokumentUnderArbeid>, List<JournalfoertDokumentReference>> {
        val parentDocument = dokumentUnderArbeidRepository.getReferenceById(parentId)

        if (parentDocument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble til et dokument som er ferdigstilt")
        }

        val behandling = behandlingService.getBehandling(behandlingId)

        val isCurrentROL = behandling.rolIdent == innloggetIdent

        journalfoerteDokumenter.forEach {
            behandlingService.connectDokumentToBehandling(
                behandlingId = behandlingId,
                journalpostId = it.journalpostId,
                dokumentInfoId = it.dokumentInfoId,
                saksbehandlerIdent = innloggetIdent,
                systemUserContext = false,
                ignoreCheckSkrivetilgang = isCurrentROL,
            )
        }

        val alreadyAddedDocuments =
            journalfoertDokumentUnderArbeidRepository.findByParentIdAndJournalfoertDokumentReferenceIsNotNull(parentId)
                .map {
                    JournalfoertDokumentReference(
                        journalpostId = it.journalpostId,
                        dokumentInfoId = it.dokumentInfoId
                    )
                }.toSet()

        val (toAdd, duplicates) = journalfoerteDokumenter.partition { it !in alreadyAddedDocuments }

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        validateCanCreateDocuments(
            behandlingRole = behandlingRole,
            parentDocument = parentDocument
        )

        val now = LocalDateTime.now()

        val resultingDocuments = toAdd.map { journalfoertDokumentReference ->
            val journalpostInDokarkiv =
                safClient.getJournalpostAsSaksbehandler(journalfoertDokumentReference.journalpostId)

            val document = JournalfoertDokumentUnderArbeidAsVedlegg(
                name = "TODO / Hentes fra SAF",
                behandlingId = behandlingId,
                parentId = parentId,
                journalpostId = journalfoertDokumentReference.journalpostId,
                dokumentInfoId = journalfoertDokumentReference.dokumentInfoId,
                creatorIdent = innloggetIdent,
                creatorRole = behandlingRole,
                opprettet = journalpostInDokarkiv.datoOpprettet,
                created = now,
                modified = now,
                markertFerdig = null,
                markertFerdigBy = null,
                ferdigstilt = null,
            )

            behandling.publishEndringsloggEvent(
                saksbehandlerident = innloggetIdent,
                felt = Felt.JOURNALFOERT_DOKUMENT_UNDER_ARBEID_OPPRETTET,
                fraVerdi = null,
                tilVerdi = document.created.toString(),
                tidspunkt = document.created,
                dokumentId = document.id,
            )

            journalfoertDokumentUnderArbeidRepository.save(
                document
            )
        }

        return resultingDocuments to duplicates
    }

    private fun Behandling.getRoleInBehandling(innloggetIdent: String) = if (rolIdent == innloggetIdent) {
        BehandlingRole.KABAL_ROL
    } else if (tildeling?.saksbehandlerident == innloggetIdent) {
        BehandlingRole.KABAL_SAKSBEHANDLING
    } else if (medunderskriver?.saksbehandlerident == innloggetIdent) {
        BehandlingRole.KABAL_MEDUNDERSKRIVER
    } else BehandlingRole.NONE

    private fun validateCanCreateDocuments(behandlingRole: BehandlingRole, parentDocument: DokumentUnderArbeid?) {
        if (behandlingRole !in listOf(BehandlingRole.KABAL_ROL, BehandlingRole.KABAL_SAKSBEHANDLING)) {
            throw MissingTilgangException("Kun ROL eller saksbehandler kan opprette dokumenter")
        }

        if (behandlingRole == BehandlingRole.KABAL_ROL && parentDocument == null) {
            throw MissingTilgangException("ROL kan ikke opprette hoveddokumenter.")
        }

        if (parentDocument != null && behandlingRole == BehandlingRole.KABAL_ROL) {
            if (!(parentDocument is SmartdokumentUnderArbeidAsVedlegg && parentDocument.smartEditorTemplateId == Template.ROL_QUESTIONS.id)) {
                throw MissingTilgangException("ROL kan ikke opprette vedlegg til dette hoveddokumentet.")
            }
        }
    }

    fun getDokumentUnderArbeid(dokumentId: UUID) = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

    fun updateDokumentType(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        dokumentType: DokumentType,
        innloggetIdent: String
    ): DokumentUnderArbeid {

        val dokumentUnderArbeid = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(dokumentUnderArbeid.behandlingId)

        if (dokumentUnderArbeid.isVedlegg()) {
            //Vi skal ikke kunne endre dokumentType på vedlegg
            throw DokumentValidationException("Man kan ikke endre dokumentType på vedlegg")
        }

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på et dokument som er ferdigstilt")
        }

        if (dokumentUnderArbeid !is DokumentUnderArbeidWithHoveddokumentCharacteristics) {
            throw RuntimeException("dokumentType cannot be set for this type of document.")
        }

        val previousValue = dokumentUnderArbeid.dokumentType
        dokumentUnderArbeid.dokumentType = dokumentType

        dokumentUnderArbeid.modified = LocalDateTime.now()

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_TYPE,
            fraVerdi = previousValue.id,
            tilVerdi = dokumentUnderArbeid.modified.toString(),
            tidspunkt = dokumentUnderArbeid.modified,
            dokumentId = dokumentUnderArbeid.id,
        )

        return dokumentUnderArbeid
    }

    private fun DokumentUnderArbeid.isVedlegg(): Boolean {
        return this is SmartdokumentUnderArbeidAsVedlegg ||
                this is OpplastetDokumentUnderArbeidAsVedlegg ||
                this is JournalfoertDokumentUnderArbeidAsVedlegg
    }

    fun updateDokumentTitle(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        dokumentTitle: String,
        innloggetIdent: String
    ): DokumentUnderArbeid {

        val dokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        val behandlingForCheck = behandlingService.getBehandling(dokument.behandlingId)

        val behandlingRole = behandlingForCheck.getRoleInBehandling(innloggetIdent)

        if (dokument.creatorRole != behandlingRole) {
            throw MissingTilgangException("$behandlingRole har ikke anledning til å endre tittel på dette dokumentet eiet av ${dokument.creatorRole}.")
        }

        val isCurrentROL = behandlingForCheck.rolIdent == innloggetIdent

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(
            behandlingId = dokument.behandlingId,
            ignoreCheckSkrivetilgang = isCurrentROL
        )

        if (dokument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre tittel på et dokument som er ferdigstilt")
        }

        val oldValue = dokument.name
        dokument.name = dokumentTitle
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_NAME,
            fraVerdi = oldValue,
            tilVerdi = dokument.name,
            tidspunkt = LocalDateTime.now(),
            dokumentId = dokument.id,
        )
        return dokument
    }

    fun validateDocument(
        dokumentId: UUID,
    ) {
        val dokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)
        if (dokument.erMarkertFerdig()) {
            throw DokumentValidationException("Dokument er allerede ferdigstilt.")
        }

        val behandling = behandlingService.getBehandling(dokument.behandlingId)

        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        when (dokument.creatorRole) {
            BehandlingRole.KABAL_SAKSBEHANDLING -> {
                if (behandlingRole !in listOf(
                        BehandlingRole.KABAL_SAKSBEHANDLING,
                        BehandlingRole.KABAL_MEDUNDERSKRIVER
                    )
                ) {
                    throw MissingTilgangException("Kun saksbehandler eller medunderskriver kan skrive i dette dokumentet.")
                }
            }

            BehandlingRole.KABAL_ROL -> {
                if (behandlingRole != BehandlingRole.KABAL_ROL) {
                    throw MissingTilgangException("Kun ROL kan skrive i dette dokumentet.")
                }
            }

            else -> {
                throw RuntimeException("A document was created by non valid role: ${dokument.creatorRole}")
            }
        }
    }

    fun updateSmartEditorTemplateId(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        templateId: String,
        innloggetIdent: String
    ): DokumentUnderArbeid {
        val dokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        if (dokument !is DokumentUnderArbeidAsSmartdokument) {
            throw RuntimeException("Not a smartdocument")
        }

        if (dokument.smartEditorTemplateId == templateId) {
            return dokument
        }

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(dokument.behandlingId)

        if (dokument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre smartEditorTemplateId på et dokument som er ferdigstilt")
        }

        val oldValue = dokument.smartEditorTemplateId
        dokument.smartEditorTemplateId = templateId
        dokument.modified = LocalDateTime.now()
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.SMARTDOKUMENT_TEMPLATE_ID,
            fraVerdi = oldValue,
            tilVerdi = dokument.smartEditorTemplateId,
            tidspunkt = LocalDateTime.now(),
            dokumentId = dokument.id,
        )
        return dokument
    }

    private fun updateJournalposter(
        behandlingId: UUID,
        dokumentId: UUID,
        journalpostIdSet: Set<DokumentUnderArbeidJournalpostId>
    ): DokumentUnderArbeid {
        val dokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        if (dokument !is DokumentUnderArbeidWithJournalposter) {
            throw RuntimeException("this document does not support journalposter.")
        }

        val behandling = behandlingService.getBehandlingForUpdateBySystembruker(behandlingId)

        val oldValue = dokument.journalposter
        dokument.journalposter.clear()
        dokument.journalposter.addAll(journalpostIdSet)

        behandling.publishEndringsloggEvent(
            saksbehandlerident = SYSTEMBRUKER,
            felt = Felt.DOKUMENT_UNDER_ARBEID_JOURNALPOST_ID,
            fraVerdi = oldValue.joinToString { it.journalpostId },
            tilVerdi = dokument.journalposter.joinToString { it.journalpostId },
            tidspunkt = LocalDateTime.now(),
            dokumentId = dokument.id,
        )

        return dokument
    }

    fun validateSmartDokument(
        dokumentId: UUID
    ): List<DocumentValidationResponse> {
        val documentValidationResults = mutableListOf<DocumentValidationResponse>()

        val hovedDokument = smartDokumentUnderArbeidRepository.getReferenceById(dokumentId)
        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)

        documentValidationResults += validateSingleSmartdocument(hovedDokument)

        vedlegg.forEach {
            if (it is SmartdokumentUnderArbeidAsVedlegg) {
                documentValidationResults += validateSingleSmartdocument(it)
            }
        }

        return documentValidationResults
    }

    private fun validateSingleSmartdocument(dokument: DokumentUnderArbeidAsSmartdokument): DocumentValidationResponse {
        logger.debug("Getting json document, dokumentId: {}", dokument.id)
        val documentJson = smartEditorApiGateway.getDocumentAsJson(dokument.smartEditorId)
        logger.debug("Validating json document in kabalJsontoPdf, dokumentId: {}", dokument.id)
        val response = kabalJsonToPdfClient.validateJsonDocument(documentJson)
        return DocumentValidationResponse(
            dokumentId = dokument.id.toString(),
            errors = response.errors.map {
                DocumentValidationResponse.DocumentValidationError(
                    type = it.type,
                    paths = it.paths,
                )
            }
        )
    }

    fun finnOgMarkerFerdigHovedDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        ident: String,
        brevmottakerIdents: Set<String>?,
    ): DokumentUnderArbeid {
        val hovedDokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        if (hovedDokument !is DokumentUnderArbeidWithHoveddokumentCharacteristics) {
            throw RuntimeException("document is not hoveddokument")
        }

        val behandling = behandlingService.getBehandlingForUpdate(hovedDokument.behandlingId)

        validateHoveddokumentBeforeFerdig(
            brevmottakerIdents = brevmottakerIdents,
            hovedDokument = hovedDokument,
            behandling = behandling,
        )
        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)

        if (hovedDokument is SmartdokumentUnderArbeid && hovedDokument.isStaleSmartEditorDokument()) {
            mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(hovedDokument)
        }
        vedlegg.forEach {
            if (it is SmartdokumentUnderArbeidAsVedlegg && it.isStaleSmartEditorDokument()) {
                mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(it)
            }
        }

        val now = LocalDateTime.now()
        hovedDokument.markerFerdigHvisIkkeAlleredeMarkertFerdig(tidspunkt = now, saksbehandlerIdent = ident)
        val mapBrevmottakerIdentToBrevmottakerInput = kabalDocumentMapper.mapBrevmottakerIdentToBrevmottakerInput(
            behandling = behandling,
            brevmottakerIdents = brevmottakerIdents,
            dokumentType = hovedDokument.dokumentType
        )
        hovedDokument.brevmottakerIdents = mapBrevmottakerIdentToBrevmottakerInput.map {
            it.partId.value
        }.toSet()

        vedlegg.forEach { it.markerFerdigHvisIkkeAlleredeMarkertFerdig(tidspunkt = now, saksbehandlerIdent = ident) }

        innholdsfortegnelseService.saveInnholdsfortegnelse(
            dokumentId,
            mapBrevmottakerIdentToBrevmottakerInput.map { it.navn })

        behandling.publishEndringsloggEvent(
            saksbehandlerident = ident,
            felt = Felt.DOKUMENT_UNDER_ARBEID_MARKERT_FERDIG,
            fraVerdi = null,
            tilVerdi = hovedDokument.markertFerdig.toString(),
            tidspunkt = LocalDateTime.now(),
            dokumentId = hovedDokument.id,
        )

        behandling.publishEndringsloggEvent(
            saksbehandlerident = ident,
            felt = Felt.DOKUMENT_UNDER_ARBEID_BREVMOTTAKER_IDENTS,
            fraVerdi = null,
            tilVerdi = hovedDokument.brevmottakerIdents.joinToString { it },
            tidspunkt = LocalDateTime.now(),
            dokumentId = hovedDokument.id,
        )

        applicationEventPublisher.publishEvent(DokumentFerdigstiltAvSaksbehandler(hovedDokument))

        return hovedDokument
    }

    private fun validateHoveddokumentBeforeFerdig(
        brevmottakerIdents: Set<String>?,
        hovedDokument: DokumentUnderArbeid,
        behandling: Behandling,
    ) {
        if (hovedDokument !is DokumentUnderArbeidWithHoveddokumentCharacteristics) {
            throw DokumentValidationException("Kan ikke markere et vedlegg som ferdig")
        }

        if (hovedDokument.erMarkertFerdig() || hovedDokument.erFerdigstilt()) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på et dokument som er ferdigstilt")
        }

        val documentValidationErrors = validateSmartDokument(hovedDokument.id)
        if (documentValidationErrors.any { it.errors.isNotEmpty() }) {
            throw JsonToPdfValidationException(
                msg = "Validation error from json to pdf",
                errors = documentValidationErrors
            )
        }

        val invalidProperties = mutableListOf<InvalidProperty>()

        if (hovedDokument.dokumentType != DokumentType.NOTAT && brevmottakerIdents.isNullOrEmpty()) {
            throw DokumentValidationException("Brevmottakere må være satt")
        }

        val mottakere = kabalDocumentMapper.mapBrevmottakerIdentToBrevmottakerInput(
            behandling = behandling,
            brevmottakerIdents = brevmottakerIdents,
            dokumentType = hovedDokument.dokumentType
        )

        //Could ignore NOTAT here. We'll see.
        mottakere.forEach { mottaker ->
            if (mottaker.partId.partIdTypeId == PartIdType.VIRKSOMHET.id) {
                val organisasjon = eregClient.hentOrganisasjon(mottaker.partId.value)
                if (!organisasjon.isActive()) {
                    invalidProperties += InvalidProperty(
                        field = mottaker.partId.value,
                        reason = "Organisasjon er avviklet.",
                    )
                }
            }
        }

        if (invalidProperties.isNotEmpty()) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Ferdigstilling av dokument",
                sections = listOf(
                    ValidationSection(
                        section = "mottakere",
                        properties = invalidProperties,
                    )
                )
            )
        }
    }

    fun getInnholdsfortegnelseAsFysiskDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        hoveddokumentId: UUID,
        innloggetIdent: String
    ): FysiskDokument {
        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandling(behandlingId)

        val title = "Innholdsfortegnelse"

        return FysiskDokument(
            title = title,
            content = dokumentService.changeTitleInPDF(
                documentBytes = innholdsfortegnelseService.getInnholdsfortegnelseAsPdf(hoveddokumentId),
                title = title
            ),
            contentType = MediaType.APPLICATION_PDF
        )
    }

    fun getFysiskDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        innloggetIdent: String
    ): FysiskDokument {
        val dokument =
            dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandling(dokument.behandlingId)

        val (content, title) = when (dokument) {
            is OpplastetDokumentUnderArbeid -> {
                mellomlagerService.getUploadedDocument(dokument.mellomlagerId!!) to dokument.name
            }

            is OpplastetDokumentUnderArbeidAsVedlegg -> {
                mellomlagerService.getUploadedDocument(dokument.mellomlagerId!!) to dokument.name
            }

            is DokumentUnderArbeidAsSmartdokument -> {
                if (dokument.isStaleSmartEditorDokument()) {
                    mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(dokument).bytes to dokument.name
                } else mellomlagerService.getUploadedDocument(dokument.mellomlagerId!!) to dokument.name
            }

            is JournalfoertDokumentUnderArbeidAsVedlegg -> {
                val fysiskDokument = dokumentService.getFysiskDokument(
                    journalpostId = dokument.journalpostId,
                    dokumentInfoId = dokument.dokumentInfoId,
                )
                fysiskDokument.content to fysiskDokument.title
            }

            else -> {
                error("can't come here")
            }
        }

        return FysiskDokument(
            title = title,
            content = dokumentService.changeTitleInPDF(content, title),
            contentType = MediaType.APPLICATION_PDF
        )
    }

    fun slettDokument(
        dokumentId: UUID,
        innloggetIdent: String,
    ) {
        val document = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandling(
            behandlingId = document.behandlingId,
        )

        smartDokumentUnderArbeidAsVedleggRepository.findByParentIdOrderByCreated(dokumentId)
            .plus(opplastetDokumentUnderArbeidAsVedleggRepository.findByParentIdOrderByCreated(dokumentId))
            .plus(journalfoertDokumentUnderArbeidRepository.findByParentIdOrderByCreated(dokumentId))
            .plus(document)
            .map {
                if (document.erMarkertFerdig()) {
                    throw MissingTilgangException("Attempting to delete finalized document ${document.id}")
                }
                document
            }
            .forEach { dokumentUnderArbeid ->
                slettEnkeltdokument(
                    document = dokumentUnderArbeid,
                    innloggetIdent = innloggetIdent,
                    behandlingRole = behandling.getRoleInBehandling(innloggetIdent),
                    behandling = behandling,
                )
            }
    }

    private fun slettEnkeltdokument(
        document: DokumentUnderArbeid,
        innloggetIdent: String,
        behandlingRole: BehandlingRole,
        behandling: Behandling,
    ) {
        if (document.creatorRole != behandlingRole) {
            throw MissingTilgangException("$behandlingRole har ikke anledning til å slette dokumentet eiet av ${document.creatorRole}.")
        }

        if (document is DokumentUnderArbeidAsMellomlagret) {
            try {
                if (document.mellomlagerId != null) {
                    mellomlagerService.deleteDocument(document.mellomlagerId!!)
                }
            } catch (e: Exception) {
                logger.warn("Couldn't delete mellomlager document", e)
            }
        }

        if (document is DokumentUnderArbeidAsSmartdokument) {
            try {
                smartEditorApiGateway.deleteDocument(document.smartEditorId)
            } catch (e: Exception) {
                logger.warn("Couldn't delete smartEditor document", e)
            }
        }

        dokumentUnderArbeidRepository.delete(document)

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_SLETTET,
            fraVerdi = document.modified.toString(),
            tilVerdi = null,
            tidspunkt = LocalDateTime.now(),
            dokumentId = document.id,
        )
    }

    fun setAsVedlegg(
        parentId: UUID,
        dokumentId: UUID,
        innloggetIdent: String
    ): Pair<List<DokumentUnderArbeid>, List<DokumentUnderArbeid>> {
        if (parentId == dokumentId) {
            throw DokumentValidationException("Kan ikke gjøre et dokument til vedlegg for seg selv.")
        }
        val parentDocument = dokumentUnderArbeidRepository.getReferenceById(parentId)

        val behandling = behandlingService.getBehandling(
            behandlingId = parentDocument.behandlingId,
        )

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        if (!(behandlingRole == BehandlingRole.KABAL_ROL && parentDocument.smartEditorTemplateId == Template.ROL_QUESTIONS.id)) {
            //Sjekker generell tilgang på behandlingsnivå:
            behandlingService.getBehandlingForUpdate(
                behandlingId = parentDocument.behandlingId,
            )
        }

        if (parentDocument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble til et dokument som er ferdigstilt")
        }

        val descendants = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(dokumentId)

        val dokumentIdSet = mutableSetOf<UUID>()
        dokumentIdSet += dokumentId
        dokumentIdSet.addAll(descendants.map { it.id })

        val processedDokumentUnderArbeidOutput = dokumentIdSet.map { currentDokumentId ->
            setParentInDokumentUnderArbeidAndFindDuplicates(currentDokumentId, parentId)
        }

        val alteredDocuments = processedDokumentUnderArbeidOutput.mapNotNull { it.first }
        val duplicateJournalfoerteDokumenterUnderArbeid = processedDokumentUnderArbeidOutput.mapNotNull { it.second }

        duplicateJournalfoerteDokumenterUnderArbeid.forEach {
            dokumentUnderArbeidRepository.deleteById(it.id)
        }

        return alteredDocuments to duplicateJournalfoerteDokumenterUnderArbeid
    }

    private fun setParentInDokumentUnderArbeidAndFindDuplicates(
        currentDokumentId: UUID,
        parentId: UUID,
    ): Pair<DokumentUnderArbeid?, DokumentUnderArbeid?> {
        val dokumentUnderArbeid =
            dokumentUnderArbeidRepository.getReferenceById(currentDokumentId)

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble et dokument som er ferdigstilt")
        }

        return if (dokumentUnderArbeid.getType() == DokumentUnderArbeid.DokumentUnderArbeidType.JOURNALFOERT) {
            if (dokumentUnderArbeidRepository.findByParentIdAndJournalfoertDokumentReferenceAndIdNot(
                    parentId = parentId,
                    journalfoertDokumentReference = dokumentUnderArbeid.journalfoertDokumentReference!!,
                    id = currentDokumentId,
                ).isNotEmpty()
            ) {
                logger.warn("Dette journalførte dokumentet er allerede lagt til som vedlegg på dette dokumentet.")
                null to dokumentUnderArbeid
            } else {
                dokumentUnderArbeid.parentId = parentId
                dokumentUnderArbeid to null
            }
        } else {
            dokumentUnderArbeid.parentId = parentId
            dokumentUnderArbeid to null
        }
    }

    fun setAsHoveddokument(
        behandlingId: UUID,
        dokumentId: UUID,
        innloggetIdent: String
    ): DokumentUnderArbeid {
        val vedlegg = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingForUpdate(
            behandlingId = vedlegg.behandlingId,
        )
        //TODO: Skal det lages endringslogg på dette??

        if (vedlegg.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke frikoble et dokument som er ferdigstilt")
        }

        val savedDocument = when (vedlegg) {
            is OpplastetDokumentUnderArbeidAsVedlegg -> {
                val new = opplastetDokumentUnderArbeidRepository.save(
                    OpplastetDokumentUnderArbeid(
                        size = vedlegg.size,
                        dokumentType = DokumentType.BREV, //TODO default? UI fix?
                        dokumentEnhetId = null,
                        brevmottakerIdents = setOf(),
                        journalposter = mutableSetOf(),
                        mellomlagerId = vedlegg.mellomlagerId,
                        name = vedlegg.name,
                        behandlingId = vedlegg.behandlingId,
                        created = vedlegg.created,
                        modified = vedlegg.modified,
                        markertFerdig = null,
                        markertFerdigBy = null,
                        ferdigstilt = null,
                        creatorIdent = vedlegg.creatorIdent,
                        creatorRole = vedlegg.creatorRole,
                    )
                )
                opplastetDokumentUnderArbeidAsVedleggRepository.delete(vedlegg)
                new
            }

            is SmartdokumentUnderArbeidAsVedlegg -> {
                val new = smartDokumentUnderArbeidRepository.save(
                    SmartdokumentUnderArbeid(
                        size = vedlegg.size,
                        smartEditorId = vedlegg.smartEditorId,
                        smartEditorTemplateId = vedlegg.smartEditorTemplateId,
                        dokumentType = DokumentType.BREV, //TODO default or UI fix?
                        dokumentEnhetId = null,
                        brevmottakerIdents = setOf(),
                        journalposter = mutableSetOf(),
                        mellomlagerId = vedlegg.mellomlagerId,
                        name = vedlegg.name,
                        behandlingId = vedlegg.behandlingId,
                        created = vedlegg.created,
                        modified = vedlegg.modified,
                        markertFerdig = null,
                        markertFerdigBy = null,
                        ferdigstilt = null,
                        creatorIdent = vedlegg.creatorIdent,
                        creatorRole = vedlegg.creatorRole,
                    )
                )
                smartDokumentUnderArbeidAsVedleggRepository.delete(vedlegg)
                new
            }

            else -> {
                error("Document could not be set as hoveddokument")
            }
        }

        return savedDocument
    }

    fun findDokumenterNotFinished(behandlingId: UUID, checkReadAccess: Boolean = true): List<DokumentUnderArbeid> {
        //Sjekker tilgang på behandlingsnivå:
        if (checkReadAccess) {
            behandlingService.getBehandling(behandlingId)
        }

        return dokumentUnderArbeidRepository.findByBehandlingIdAndFerdigstiltIsNullOrderByCreatedDesc(behandlingId)
    }

    fun getSmartDokumenterUnderArbeid(behandlingId: UUID, ident: String): SortedSet<DokumentUnderArbeid> {
        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandling(behandlingId)

        return smartDokumentUnderArbeidRepository.findByBehandlingIdAndSmartEditorIdNotNullAndMarkertFerdigIsNullOrderByCreated(
            behandlingId
        )
    }

    fun opprettDokumentEnhet(hovedDokumentId: UUID): DokumentUnderArbeid {
        val hovedDokument = dokumentUnderArbeidRepository.getReferenceById(hovedDokumentId)
        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)
        //Denne er alltid sann
        if (hovedDokument.dokumentEnhetId == null) {
            //Vi vet at smartEditor-dokumentene har en oppdatert snapshot i mellomlageret fordi det ble fikset i finnOgMarkerFerdigHovedDokument
            val behandling = behandlingService.getBehandlingForUpdateBySystembruker(hovedDokument.behandlingId)
            val dokumentEnhetId = kabalDocumentGateway.createKomplettDokumentEnhet(
                behandling = behandling,
                hovedDokument = hovedDokument,
                vedlegg = vedlegg,
                innholdsfortegnelse = innholdsfortegnelseService.getInnholdsfortegnelse(hovedDokumentId)
            )
            hovedDokument.dokumentEnhetId = dokumentEnhetId
        }
        return hovedDokument
    }

    fun ferdigstillDokumentEnhet(hovedDokumentId: UUID): DokumentUnderArbeid {
        val hovedDokument = dokumentUnderArbeidRepository.getReferenceById(hovedDokumentId)
        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)
        val behandling: Behandling = behandlingService.getBehandlingForUpdateBySystembruker(hovedDokument.behandlingId)
        val documentInfoList =
            kabalDocumentGateway.fullfoerDokumentEnhet(dokumentEnhetId = hovedDokument.dokumentEnhetId!!)

        documentInfoList.forEach { documentInfo ->
            val journalpost = safClient.getJournalpostAsSystembruker(documentInfo.journalpostId.value)

            val saksdokumenter = journalpost.mapToSaksdokumenter()
            saksdokumenter.forEach { saksdokument ->
                val saksbehandlerIdent = SYSTEMBRUKER
                behandling.addSaksdokument(saksdokument, saksbehandlerIdent)
                    ?.also { applicationEventPublisher.publishEvent(it) }
            }
        }

        updateJournalposter(
            behandlingId = behandling.id,
            dokumentId = hovedDokumentId,
            journalpostIdSet = HashSet(documentInfoList.map { DokumentUnderArbeidJournalpostId(journalpostId = it.journalpostId.value) })
        )

        val now = LocalDateTime.now()

        hovedDokument.ferdigstillHvisIkkeAlleredeFerdigstilt(now)
        if (hovedDokument.smartEditorId != null) {
            try {
                smartEditorApiGateway.deleteDocumentAsSystemUser(hovedDokument.smartEditorId!!)
            } catch (e: Exception) {
                logger.warn("Couldn't delete hoveddokument from smartEditorApi", e)
            }
        }

        vedlegg.forEach {
            it.ferdigstillHvisIkkeAlleredeFerdigstilt(now)
            try {
                smartEditorApiGateway.deleteDocumentAsSystemUser(it.smartEditorId!!)
            } catch (e: Exception) {
                logger.warn("Couldn't delete vedlegg from smartEditorApi", e)
            }
        }

        return hovedDokument
    }

    fun getSmartEditorId(dokumentId: UUID, readOnly: Boolean): UUID {
        val dokumentUnderArbeid = dokumentUnderArbeidRepository.getReferenceById(dokumentId)
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()

        if (dokumentUnderArbeid !is DokumentUnderArbeidAsSmartdokument) {
            throw RuntimeException("dokument is not smartdokument")
        }

        //Sjekker tilgang på behandlingsnivå:
        if (readOnly) {
            behandlingService.getBehandling(dokumentUnderArbeid.behandlingId)
        } else {
            behandlingService.getBehandlingForWriteAllowROLAndMU(
                behandlingId = dokumentUnderArbeid.behandlingId,
                utfoerendeSaksbehandlerIdent = ident,
            )
        }

        return dokumentUnderArbeid.smartEditorId
    }

    private fun mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(dokument: DokumentUnderArbeid): PDFDocument {

        if (dokument !is DokumentUnderArbeidAsSmartdokument) {
            throw RuntimeException("dokument is not smartdokument")
        }

        val documentJson = smartEditorApiGateway.getDocumentAsJson(dokument.smartEditorId)
        val pdfDocument = kabalJsonToPdfClient.getPDFDocument(documentJson)

        val mellomlagerId =
            mellomlagerService.uploadByteArray(
                tittel = dokument.name,
                content = pdfDocument.bytes
            )

        if (dokument.mellomlagerId != null) {
            mellomlagerService.deleteDocument(dokument.mellomlagerId!!)
        }

        dokument.mellomlagerId = mellomlagerId
        dokument.size = pdfDocument.bytes.size.toLong()
        dokument.modified = LocalDateTime.now()

        return pdfDocument
    }

    private fun DokumentUnderArbeidAsSmartdokument.isStaleSmartEditorDokument() =
        smartEditorApiGateway.isMellomlagretDokumentStale(
            smartEditorId = this.smartEditorId,
            sistOpplastet = this.modified, //TODO changed from opplastet. Verify.
        )

    private fun Behandling.endringslogg(
        saksbehandlerident: String,
        felt: Felt,
        fraVerdi: String?,
        tilVerdi: String?,
        tidspunkt: LocalDateTime
    ): Endringslogginnslag? {
        return Endringslogginnslag.endringslogg(
            saksbehandlerident,
            felt,
            fraVerdi,
            tilVerdi,
            this.id,
            tidspunkt
        )
    }

    private fun Behandling.publishEndringsloggEvent(
        saksbehandlerident: String,
        felt: Felt,
        fraVerdi: String?,
        tilVerdi: String?,
        tidspunkt: LocalDateTime,
        dokumentId: UUID,
    ) {
        listOfNotNull(
            this.endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.DOKUMENT_UNDER_ARBEID_ID,
                fraVerdi = fraVerdi.let { dokumentId.toString() },
                tilVerdi = tilVerdi.let { dokumentId.toString() },
                tidspunkt = tidspunkt,
            ),
            this.endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = felt,
                fraVerdi = fraVerdi,
                tilVerdi = tilVerdi,
                tidspunkt = tidspunkt,
            )
        ).let {
            applicationEventPublisher.publishEvent(
                BehandlingEndretEvent(
                    behandling = this,
                    endringslogginnslag = it
                )
            )
        }
    }

    private fun Journalpost?.mapToSaksdokumenter(): List<Saksdokument> {
        return this?.dokumenter?.map {
            Saksdokument(
                journalpostId = this.journalpostId,
                dokumentInfoId = it.dokumentInfoId
            )
        } ?: emptyList()
    }
}


