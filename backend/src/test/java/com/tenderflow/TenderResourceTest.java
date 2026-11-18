package com.tenderflow;

import com.tenderflow.service.TenderLifecycleService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TenderResourceTest {

    @Inject
    TenderLifecycleService lifecycleService;

    private static String buyerToken;
    private static String otherBuyerToken;
    private static String supplierToken;

    @BeforeAll
    public static void setupTokens() {
        buyerToken = Jwt.issuer("https://tenderflow.it/issuer")
                .upn("buyer@tenderflow.it")
                .groups(Set.of("BUYER"))
                .claim("fullName", "Buyer Test Srl")
                .expiresIn(3600)
                .sign();

        otherBuyerToken = Jwt.issuer("https://tenderflow.it/issuer")
                .upn("altro-buyer@tenderflow.it")
                .groups(Set.of("BUYER"))
                .claim("fullName", "Altro Ente SpA")
                .expiresIn(3600)
                .sign();

        supplierToken = Jwt.issuer("https://tenderflow.it/issuer")
                .upn("supplier@tenderflow.it")
                .groups(Set.of("SUPPLIER"))
                .claim("fullName", "Supplier Test SpA")
                .expiresIn(3600)
                .sign();
    }

    @Test
    public void testUnauthenticatedAccessFails() {
        String tenderJson = """
            {
                "title": "Gara Non Autorizzata",
                "budget": 10000.00,
                "status": "OPEN"
            }
            """;

        given()
                .contentType(ContentType.JSON)
                .body(tenderJson)
                .when()
                .post("/api/tenders")
                .then()
                .statusCode(401);
    }

    @Test
    public void testGlobalExceptionMapperErrorFormat() {
        given()
                .auth().oauth2(buyerToken)
                .contentType(ContentType.JSON)
                .when()
                .put("/api/tenders/999999/close")
                .then()
                .statusCode(404)
                .body("status", is(404))
                .body("error", is("Not Found"))
                .body("message", is("Gara non trovata"))
                .body("timestamp", notNullValue());
    }

    @Test
    public void testRoleAccessOwnershipAndCompleteLifecycle() {
        String draftTenderJson = """
            {
                "title": "Gara Cyber Security",
                "description": "Fornitura SOC Managed",
                "budget": 60000.00,
                "closingDate": "2026-12-31",
                "status": "DRAFT"
            }
            """;

        // 1. SUPPLIER tenta di creare una gara -> 403 Forbidden
        given()
                .auth().oauth2(supplierToken)
                .contentType(ContentType.JSON)
                .body(draftTenderJson)
                .when()
                .post("/api/tenders")
                .then()
                .statusCode(403);

        // 2. BUYER crea la gara in bozza (DRAFT) -> 201 Created
        Integer tenderId = given()
                .auth().oauth2(buyerToken)
                .contentType(ContentType.JSON)
                .body(draftTenderJson)
                .when()
                .post("/api/tenders")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("status", is("DRAFT"))
                .body("createdByEmail", is("buyer@tenderflow.it"))
                .extract()
                .path("id");

        // 3. Altro BUYER tenta di pubblicare la gara non sua -> 403 Forbidden
        given()
                .auth().oauth2(otherBuyerToken)
                .when()
                .put("/api/tenders/" + tenderId + "/publish")
                .then()
                .statusCode(403);

        // 4. BUYER creatore pubblica la gara (DRAFT -> OPEN) -> 200 OK
        given()
                .auth().oauth2(buyerToken)
                .when()
                .put("/api/tenders/" + tenderId + "/publish")
                .then()
                .statusCode(200)
                .body("status", is("OPEN"));

        // 5. SUPPLIER invia prima offerta valida -> 201 Created
        given()
                .auth().oauth2(supplierToken)
                .contentType(ContentType.JSON)
                .body("""
                    { "amount": 45000.00 }
                    """)
                .when()
                .post("/api/tenders/" + tenderId + "/bids")
                .then()
                .statusCode(201)
                .body("bidderName", is("Supplier Test SpA"));

        // 6. SUPPLIER tenta offerta peggiorativa -> 400 Bad Request
        given()
                .auth().oauth2(supplierToken)
                .contentType(ContentType.JSON)
                .body("""
                    { "amount": 48000.00 }
                    """)
                .when()
                .post("/api/tenders/" + tenderId + "/bids")
                .then()
                .statusCode(400);

        // 7. SUPPLIER migliora l'offerta al ribasso -> 200 OK
        given()
                .auth().oauth2(supplierToken)
                .contentType(ContentType.JSON)
                .body("""
                    { "amount": 42000.00 }
                    """)
                .when()
                .post("/api/tenders/" + tenderId + "/bids")
                .then()
                .statusCode(200)
                .body("amount", is(42000.0f));

        // 8. Altro BUYER tenta di chiudere la gara non sua -> 403 Forbidden
        given()
                .auth().oauth2(otherBuyerToken)
                .when()
                .put("/api/tenders/" + tenderId + "/close")
                .then()
                .statusCode(403);

        // 9. BUYER creatore chiude la gara -> 200 OK con aggiudicazione
        given()
                .auth().oauth2(buyerToken)
                .when()
                .put("/api/tenders/" + tenderId + "/close")
                .then()
                .statusCode(200)
                .body("winningBid.bidderName", is("Supplier Test SpA"))
                .body("winningBid.amount", is(42000.0f));
    }

    @Test
    public void testSchedulerClosesExpiredTenders() {
        String expiredTenderJson = """
            {
                "title": "Gara Manutenzione Scaduta",
                "description": "Test chiusura automatica scheduler",
                "budget": 20000.00,
                "closingDate": "2020-01-01",
                "status": "OPEN"
            }
            """;

        Integer tenderId = given()
                .auth().oauth2(buyerToken)
                .contentType(ContentType.JSON)
                .body(expiredTenderJson)
                .when()
                .post("/api/tenders")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        int closed = lifecycleService.closeExpiredTenders();
        assertTrue(closed >= 1);

        given()
                .queryParam("title", "Gara Manutenzione Scaduta")
                .when()
                .get("/api/tenders")
                .then()
                .statusCode(200)
                .body("[0].id", is(tenderId))
                .body("[0].status", is("CLOSED"));
    }

    @Test
    public void testUploadAndDownloadTenderPdfDocument() {
        String tenderJson = """
            {
                "title": "Gara Infrastruttura Cloud",
                "description": "Gara con capitolato PDF",
                "budget": 90000.00,
                "closingDate": "2026-12-31",
                "status": "OPEN"
            }
            """;

        Integer tenderId = given()
                .auth().oauth2(buyerToken)
                .contentType(ContentType.JSON)
                .body(tenderJson)
                .when()
                .post("/api/tenders")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        byte[] fakePdfBytes = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        // Altro buyer non può caricare il capitolato -> 403 Forbidden
        given()
                .auth().oauth2(otherBuyerToken)
                .multiPart("file", "capitolato-abusivo.pdf", fakePdfBytes, "application/pdf")
                .when()
                .post("/api/tenders/" + tenderId + "/document")
                .then()
                .statusCode(403);

        // Buyer proprietario carica il capitolato PDF -> 200 OK
        given()
                .auth().oauth2(buyerToken)
                .multiPart("file", "capitolato-tecnico.pdf", fakePdfBytes, "application/pdf")
                .when()
                .post("/api/tenders/" + tenderId + "/document")
                .then()
                .statusCode(200)
                .body("documentOriginalName", is("capitolato-tecnico.pdf"));

        // Download del PDF -> 200 OK application/pdf
        given()
                .when()
                .get("/api/tenders/" + tenderId + "/document")
                .then()
                .statusCode(200)
                .contentType("application/pdf");
    }
}