package com.tenderflow.controller;

import com.tenderflow.exception.BusinessException;
import com.tenderflow.model.Bid;
import com.tenderflow.model.Tender;
import com.tenderflow.service.TenderLifecycleService;
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

import java.math.BigDecimal;
import java.util.List;

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
}