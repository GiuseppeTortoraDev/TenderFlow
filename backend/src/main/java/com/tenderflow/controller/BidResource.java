package com.tenderflow.controller;

import com.tenderflow.exception.BusinessException;
import com.tenderflow.model.Bid;
import com.tenderflow.model.Tender;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Path("/api/tenders/{tenderId}/bids")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Bid Management", description = "Sottomissione e consultazione offerte per gara")
public class BidResource {

    @Inject
    JsonWebToken jwt;

    @Channel("bid-events-out")
    Emitter<Bid> bidEmitter;

    @POST
    @RolesAllowed("SUPPLIER")
    @Transactional
    @SecurityRequirement(name = "jwtAuth")
    @Operation(summary = "Invia o aggiorna al ribasso un'offerta (Solo SUPPLIER)", description = "Richiede il ruolo SUPPLIER.")
    public Response createBid(
            @Parameter(description = "ID della gara") @PathParam("tenderId") Long tenderId,
            Bid bid) {

        Tender tender = Tender.findById(tenderId);
        if (tender == null) {
            throw new BusinessException("Gara non trovata", Response.Status.NOT_FOUND);
        }

        if (!"OPEN".equalsIgnoreCase(tender.status)) {
            throw new BusinessException("Impossibile inviare offerte per una gara non aperta");
        }

        if (tender.closingDate != null && LocalDate.now().isAfter(tender.closingDate)) {
            throw new BusinessException("Il termine per la presentazione delle offerte per questa gara e' scaduto");
        }

        if (bid.amount == null || bid.amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("L'importo dell'offerta deve essere maggiore di zero");
        }

        if (tender.budget != null && bid.amount.compareTo(tender.budget) > 0) {
            throw new BusinessException("L'importo dell'offerta supera il budget massimo della gara");
        }

        String authenticatedEmail = jwt.getName();
        String authenticatedName = jwt.getClaim("fullName");
        if (authenticatedName == null || authenticatedName.isBlank()) {
            authenticatedName = authenticatedEmail != null ? authenticatedEmail : bid.bidderName;
        }

        // Verifica se il fornitore ha già inviato un'offerta su questa gara
        Bid existingBid = (authenticatedEmail != null)
                ? Bid.find("tender.id = ?1 and bidderEmail = ?2", tenderId, authenticatedEmail).firstResult()
                : null;

        if (existingBid != null) {
            if (bid.amount.compareTo(existingBid.amount) >= 0) {
                throw new BusinessException("Hai gia' inviato un'offerta di € " + existingBid.amount + ". Puoi solo migliorarla al ribasso.");
            }
            existingBid.amount = bid.amount;
            existingBid.bidderName = authenticatedName;
            existingBid.submissionDate = LocalDateTime.now();

            bidEmitter.send(existingBid);
            return Response.ok(existingBid).build();
        }

        bid.bidderEmail = authenticatedEmail;
        bid.bidderName = authenticatedName;
        bid.tender = tender;
        bid.submissionDate = LocalDateTime.now();
        bid.persist();

        bidEmitter.send(bid);

        return Response.status(Response.Status.CREATED).entity(bid).build();
    }

    @GET
    @RolesAllowed({"BUYER", "SUPPLIER"})
    @SecurityRequirement(name = "jwtAuth")
    @Operation(summary = "Recupera offerte di una gara", description = "Accessibile da utenti autenticati come BUYER o SUPPLIER con paginazione e ordinamento.")
    public List<Bid> getBidsForTender(
            @Parameter(description = "ID della gara") @PathParam("tenderId") Long tenderId,
            @Parameter(description = "Numero di pagina (0-indexed)") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Elementi per pagina") @QueryParam("size") @DefaultValue("10") int size,
            @Parameter(description = "Campo di ordinamento (es. amount, submissionDate)") @QueryParam("sortBy") @DefaultValue("amount") String sortBy,
            @Parameter(description = "Direzione dell'ordinamento (asc o desc)") @QueryParam("sortOrder") @DefaultValue("asc") String sortOrder) {

        Sort sort = "desc".equalsIgnoreCase(sortOrder) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        return Bid.find("tender.id = ?1", sort, tenderId)
                .page(Page.of(page, size))
                .list();
    }
}