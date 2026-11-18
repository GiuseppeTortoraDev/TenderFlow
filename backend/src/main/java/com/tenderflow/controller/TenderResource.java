package com.tenderflow.controller;

import com.tenderflow.dto.TenderStatsDTO;
import com.tenderflow.exception.BusinessException;
import com.tenderflow.model.Bid;
import com.tenderflow.model.Tender;
import com.tenderflow.service.DocumentStorageService;
import com.tenderflow.service.TenderLifecycleService;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/tenders")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Tender Management", description = "Operazioni di gestione, ricerca e aggiudicazione delle gare d'appalto")
@SecurityScheme(securitySchemeName = "jwtAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class TenderResource {

    @Inject
    JsonWebToken jwt;
    @Inject
    TenderLifecycleService lifecycleService;
    @Inject
    DocumentStorageService documentStorageService;

    @Channel("tender-events-out")
    Emitter<Tender> tenderEmitter;

    @GET
    @PermitAll
    @Operation(summary = "Lista gare con filtri, paginazione e ordinamento", description = "Mostra le gare pubbliche e le bozze (DRAFT) del solo buyer creatore.")
    public List<Tender> getTenders(
            @Parameter(description = "Stato della gara (es. DRAFT, OPEN, CLOSED)") @QueryParam("status") String status,
            @Parameter(description = "Budget minimo") @QueryParam("minBudget") BigDecimal minBudget,
            @Parameter(description = "Budget massimo") @QueryParam("maxBudget") BigDecimal maxBudget,
            @Parameter(description = "Parola chiave nel titolo") @QueryParam("title") String title,
            @Parameter(description = "Numero di pagina (0-indexed)") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Elementi per pagina") @QueryParam("size") @DefaultValue("10") int size,
            @Parameter(description = "Campo per l'ordinamento (es. id, budget, title, closingDate)") @QueryParam("sortBy") @DefaultValue("id") String sortBy,
            @Parameter(description = "Direzione dell'ordinamento (asc o desc)") @QueryParam("sortOrder") @DefaultValue("asc") String sortOrder) {

        String currentUserEmail = jwt != null ? jwt.getName() : null;

        StringBuilder query = new StringBuilder("1=1");
        Parameters params = new Parameters();

        // Le gare in DRAFT sono visibili solo al buyer che le ha create
        if (currentUserEmail != null && !currentUserEmail.isBlank()) {
            query.append(" and (status <> 'DRAFT' or createdByEmail = :currentUserEmail)");
            params.and("currentUserEmail", currentUserEmail);
        } else {
            query.append(" and status <> 'DRAFT'");
        }

        if (status != null && !status.isBlank()) {
            query.append(" and status = :status");
            params.and("status", status.toUpperCase());
        }
        if (minBudget != null) {
            query.append(" and budget >= :minBudget");
            params.and("minBudget", minBudget);
        }
        if (maxBudget != null) {
            query.append(" and budget <= :maxBudget");
            params.and("maxBudget", maxBudget);
        }
        if (title != null && !title.isBlank()) {
            query.append(" and lower(title) like :title");
            params.and("title", "%" + title.toLowerCase() + "%");
        }

        Sort sort = "desc".equalsIgnoreCase(sortOrder) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        return Tender.find(query.toString(), sort, params)
                .page(Page.of(page, size))
                .list();
    }

    @POST
    @RolesAllowed("BUYER")
    @Transactional
    @SecurityRequirement(name = "jwtAuth")
    @Operation(summary = "Crea una nuova gara (Solo BUYER)", description = "Richiede ruolo BUYER.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Gara creata con successo"),
            @APIResponse(responseCode = "401", description = "Non autenticato"),
            @APIResponse(responseCode = "403", description = "Ruolo insufficiente (richiesto BUYER)")
    })
    public Response create(Tender tender) {
        if (tender.id != null) {
            tender.id = null;
        }

        String normalizedStatus = (tender.status == null || tender.status.isBlank())
                ? "OPEN"
                : tender.status.toUpperCase();

        if (!normalizedStatus.equals("DRAFT") && !normalizedStatus.equals("OPEN")) {
            throw new BusinessException("Stato iniziale non valido. Usa DRAFT o OPEN");
        }

        String creatorEmail = jwt.getName();
        String creatorName = jwt.getClaim("fullName");

        tender.status = normalizedStatus;
        tender.createdByEmail = creatorEmail;
        tender.createdByName = (creatorName != null && !creatorName.isBlank()) ? creatorName : creatorEmail;
        tender.persist();

        // Pubblica evento Kafka solo se la gara è effettivamente aperta al pubblico
        if ("OPEN".equals(tender.status)) {
            tenderEmitter.send(tender);
        }

        return Response.status(Response.Status.CREATED).entity(tender).build();
    }

    @PUT
    @Path("/{id}/publish")
    @RolesAllowed("BUYER")
    @Transactional
    @SecurityRequirement(name = "jwtAuth")
    @Operation(summary = "Pubblica una gara in bozza (Solo BUYER creatore)", description = "Passa lo stato da DRAFT a OPEN.")
    public Response publishTender(@Parameter(description = "ID della gara") @PathParam("id") Long id) {
        Tender tender = Tender.findById(id);
        if (tender == null) {
            throw new BusinessException("Gara non trovata", Response.Status.NOT_FOUND);
        }

        verifyOwnership(tender);

        if (!"DRAFT".equalsIgnoreCase(tender.status)) {
            throw new BusinessException("Solo le gare in stato DRAFT possono essere pubblicate");
        }

        tender.status = "OPEN";
        tenderEmitter.send(tender);

        return Response.ok(tender).build();
    }

    @PUT
    @Path("/{id}/close")
    @RolesAllowed("BUYER")
    @Transactional
    @SecurityRequirement(name = "jwtAuth")
    @Operation(summary = "Chiudi gara ed aggiudica (Solo BUYER creatore)", description = "Richiede ruolo BUYER e ownership della gara.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Gara chiusa ed aggiudicata"),
            @APIResponse(responseCode = "401", description = "Non autenticato"),
            @APIResponse(responseCode = "403", description = "Non sei il proprietario della gara"),
            @APIResponse(responseCode = "404", description = "Gara non trovata")
    })
    public Response closeTender(@Parameter(description = "ID della gara") @PathParam("id") Long id) {
        Tender tender = Tender.findById(id);
        if (tender == null) {
            throw new BusinessException("Gara non trovata", Response.Status.NOT_FOUND);
        }

        verifyOwnership(tender);

        if ("DRAFT".equalsIgnoreCase(tender.status)) {
            throw new BusinessException("Impossibile chiudere una gara ancora in bozza (DRAFT)");
        }

        if ("CLOSED".equalsIgnoreCase(tender.status)) {
            throw new BusinessException("La gara e' gia' stata chiusa");
        }

        Bid winningBid = lifecycleService.closeAndAwardTender(tender);

        if (winningBid == null) {
            return Response.ok()
                    .entity("{\"message\": \"Gara chiusa. Nessuna offerta pervenuta.\", \"tenderId\": " + id + "}")
                    .build();
        }

        return Response.ok(new TenderAwardResult(tender, winningBid)).build();
    }

    private void verifyOwnership(Tender tender) {
        String currentUserEmail = jwt.getName();
        if (tender.createdByEmail != null && !tender.createdByEmail.equalsIgnoreCase(currentUserEmail)) {
            throw new BusinessException("Operazione non consentita: non sei il proprietario di questa gara", Response.Status.FORBIDDEN);
        }
    }

    public record TenderAwardResult(Tender tender, Bid winningBid) {}

    @POST
    @Path("/{id}/document")
    @RolesAllowed("BUYER")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @SecurityRequirement(name = "jwtAuth")
    @Operation(summary = "Carica capitolato tecnico PDF (Solo BUYER creatore)", description = "Allega o sostituisce un documento PDF (max 10 MB) alla gara.")
    public Response uploadDocument(
            @Parameter(description = "ID della gara") @PathParam("id") Long id,
            @RestForm("file") FileUpload file) {

        Tender tender = Tender.findById(id);
        if (tender == null) {
            throw new BusinessException("Gara non trovata", Response.Status.NOT_FOUND);
        }

        verifyOwnership(tender);

        if (file == null || file.uploadedFile() == null) {
            throw new BusinessException("Seleziona un file PDF da caricare");
        }

        try (InputStream is = Files.newInputStream(file.uploadedFile())) {
            String previousStoredName = tender.documentStoredName;
            String newStoredName = documentStorageService.storePdf(id, file.fileName(), is);

            tender.documentOriginalName = file.fileName();
            tender.documentStoredName = newStoredName;

            if (previousStoredName != null) {
                documentStorageService.deleteQuietly(previousStoredName);
            }

            return Response.ok(tender).build();
        } catch (IOException e) {
            throw new BusinessException("Impossibile leggere il file caricato", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("/{id}/document")
    @PermitAll
    @Produces("application/pdf")
    @Operation(summary = "Scarica o visualizza il capitolato tecnico PDF", description = "Restituisce il PDF allegato alla gara se presente.")
    public Response downloadDocument(@Parameter(description = "ID della gara") @PathParam("id") Long id) {
        Tender tender = Tender.findById(id);
        if (tender == null) {
            throw new BusinessException("Gara non trovata", Response.Status.NOT_FOUND);
        }

        java.nio.file.Path filePath = documentStorageService.resolveDocumentPath(tender.documentStoredName);
        String downloadName = (tender.documentOriginalName != null && !tender.documentOriginalName.isBlank())
                ? tender.documentOriginalName.replace("\"", "")
                : "capitolato-gara-" + id + ".pdf";

        return Response.ok(filePath.toFile(), "application/pdf")
                .header("Content-Disposition", "inline; filename=\"" + downloadName + "\"")
                .build();
    }

    @GET
    @Path("/stats")
    @PermitAll
    @Operation(summary = "Statistiche e KPI procurement", description = "Restituisce metriche aggregate su gare, volumi aggiudicati, risparmio medio e top fornitori.")
    public TenderStatsDTO getStats() {
        List<Tender> allTenders = Tender.listAll();
        long totalBids = Bid.count();

        long openCount = allTenders.stream().filter(t -> "OPEN".equalsIgnoreCase(t.status)).count();
        long draftCount = allTenders.stream().filter(t -> "DRAFT".equalsIgnoreCase(t.status)).count();
        long closedCount = allTenders.stream().filter(t -> "CLOSED".equalsIgnoreCase(t.status)).count();

        BigDecimal totalOpenBudget = allTenders.stream()
                .filter(t -> "OPEN".equalsIgnoreCase(t.status) && t.budget != null)
                .map(t -> t.budget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Tender> awardedTenders = allTenders.stream()
                .filter(t -> "CLOSED".equalsIgnoreCase(t.status)
                        && t.winningAmount != null
                        && t.budget != null
                        && t.budget.compareTo(BigDecimal.ZERO) > 0)
                .toList();

        BigDecimal totalAwardedVolume = awardedTenders.stream()
                .map(t -> t.winningAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAwardedBudget = awardedTenders.stream()
                .map(t -> t.budget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSavingsAmount = totalAwardedBudget.subtract(totalAwardedVolume).max(BigDecimal.ZERO);

        double averageSavingsPercentage = 0.0;
        if (!awardedTenders.isEmpty()) {
            double sumPct = awardedTenders.stream()
                    .mapToDouble(t -> t.budget.subtract(t.winningAmount)
                            .divide(t.budget, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue())
                    .sum();
            averageSavingsPercentage = Math.round((sumPct / awardedTenders.size()) * 100.0) / 100.0;
        }

        Map<String, List<Tender>> byWinner = awardedTenders.stream()
                .filter(t -> t.winningBidderName != null && !t.winningBidderName.isBlank())
                .collect(Collectors.groupingBy(t -> t.winningBidderName));

        List<TenderStatsDTO.TopSupplierDTO> topSuppliers = byWinner.entrySet().stream()
                .map(entry -> new TenderStatsDTO.TopSupplierDTO(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().map(t -> t.winningAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                ))
                .sorted(Comparator.comparingLong(TenderStatsDTO.TopSupplierDTO::wonCount).reversed()
                        .thenComparing(TenderStatsDTO.TopSupplierDTO::totalWonVolume, Comparator.reverseOrder()))
                .limit(5)
                .toList();

        return new TenderStatsDTO(
                allTenders.size(),
                openCount,
                draftCount,
                closedCount,
                totalBids,
                totalOpenBudget,
                totalAwardedVolume,
                totalSavingsAmount,
                averageSavingsPercentage,
                topSuppliers
        );
    }
}