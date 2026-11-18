package com.tenderflow.config;

import com.tenderflow.model.AuditLog;
import com.tenderflow.model.Bid;
import com.tenderflow.model.Tender;
import com.tenderflow.entity.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@ApplicationScoped
public class DemoDataSeeder {

    private static final Logger LOG = Logger.getLogger(DemoDataSeeder.class);

    @Inject
    LaunchMode launchMode;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (launchMode == LaunchMode.TEST || User.count() > 0 || Tender.count() > 0) {
            return;
        }

        LOG.info("[SEEDER] Inizializzazione database professionale TenderFlow...");

        // 1. Utenti Demo (Password comune: TenderFlow2026!)
        String defaultHash = BcryptUtil.bcryptHash("TenderFlow2026!");

        User buyer1 = createUser("procurement@comune-milano.it", "Direzione Acquisti - Comune di Milano", "BUYER", defaultHash);
        User buyer2 = createUser("gare@infrastrutture-italia.it", "Ufficio Gare - Infrastrutture Italia S.p.A.", "BUYER", defaultHash);

        User sup1 = createUser("offerte@techsolutions.it", "TechSolutions Enterprise S.r.l.", "SUPPLIER", defaultHash);
        User sup2 = createUser("gare@clouditalia.it", "CloudItalia Sistemi S.p.A.", "SUPPLIER", defaultHash);
        User sup3 = createUser("commerciale@edildigitale.it", "EdilDigitale & SmartGrid S.r.l.", "SUPPLIER", defaultHash);

        // 2. Gara #1: CLOSED & AGGIUDICATA (popola subito i KPI Risparmio e Top Fornitori)
        Tender t1 = new Tender();
        t1.title = "Fornitura Infrastruttura Cloud Ibrida & Disaster Recovery";
        t1.description = "Migrazione datacenter comunale su architettura multi-region con SLA 99.99%, backup immutabile e supporto H24.";
        t1.budget = new BigDecimal("180000.00");
        t1.closingDate = LocalDate.now().minusDays(3);
        t1.status = "CLOSED";
        t1.createdByEmail = buyer1.email;
        t1.createdByName = buyer1.fullName;
        t1.winningBidderName = sup2.fullName;
        t1.winningAmount = new BigDecimal("154500.00");
        t1.persist();

        createBid(t1, sup1, new BigDecimal("168000.00"), LocalDateTime.now().minusDays(6));
        createBid(t1, sup2, new BigDecimal("154500.00"), LocalDateTime.now().minusDays(4));

        AuditLog.record(t1.id, "CREATED", buyer1.email, buyer1.fullName, "Gara creata in stato OPEN con budget € 180000.00");
        AuditLog.record(t1.id, "BID_SUBMITTED", sup1.email, sup1.fullName, "Nuova offerta economica presentata di € 168000.00");
        AuditLog.record(t1.id, "BID_SUBMITTED", sup2.email, sup2.fullName, "Nuova offerta economica presentata di € 154500.00");
        AuditLog.record(t1.id, "CLOSED_MANUAL", buyer1.email, buyer1.fullName, "Gara chiusa e aggiudicata a CloudItalia Sistemi S.p.A. per € 154500.00");

        // 3. Gara #2: OPEN con offerte attive
        Tender t2 = new Tender();
        t2.title = "Sviluppo Portale Servizi Digitali al Cittadino (PNRR)";
        t2.description = "Progettazione e sviluppo piattaforma web accessibile WCAG 2.1 AA integrata con SPID/CIE e pagoPA.";
        t2.budget = new BigDecimal("125000.00");
        t2.closingDate = LocalDate.now().plusDays(14);
        t2.status = "OPEN";
        t2.createdByEmail = buyer1.email;
        t2.createdByName = buyer1.fullName;
        t2.persist();

        createBid(t2, sup1, new BigDecimal("114000.00"), LocalDateTime.now().minusHours(18));
        createBid(t2, sup3, new BigDecimal("109500.00"), LocalDateTime.now().minusHours(6));

        AuditLog.record(t2.id, "CREATED", buyer1.email, buyer1.fullName, "Gara creata in stato OPEN con budget € 125000.00");
        AuditLog.record(t2.id, "BID_SUBMITTED", sup1.email, sup1.fullName, "Nuova offerta economica presentata di € 114000.00");
        AuditLog.record(t2.id, "BID_SUBMITTED", sup3.email, sup3.fullName, "Nuova offerta economica presentata di € 109500.00");

        // 4. Gara #3: OPEN aperta alle offerte (senza offerte ancora presentate)
        Tender t3 = new Tender();
        t3.title = "Manutenzione Evoluta Rete Sensoristica IoT & Smart Lighting";
        t3.description = "Servizio triennale di telecontrollo, manutenzione preventiva e analisi predittiva su 4.500 punti luce.";
        t3.budget = new BigDecimal("240000.00");
        t3.closingDate = LocalDate.now().plusDays(25);
        t3.status = "OPEN";
        t3.createdByEmail = buyer2.email;
        t3.createdByName = buyer2.fullName;
        t3.persist();

        AuditLog.record(t3.id, "CREATED", buyer2.email, buyer2.fullName, "Gara creata in stato OPEN con budget € 240000.00");

        // 5. Gara #4: DRAFT (visibile solo a buyer1)
        Tender t4 = new Tender();
        t4.title = "Audit Cybersecurity & Penetration Testing Infrastrutture Critiche";
        t4.description = "Bozza capitolato tecnico per assessment annuale ISO 27001 e simulazioni Red Team.";
        t4.budget = new BigDecimal("65000.00");
        t4.closingDate = LocalDate.now().plusDays(30);
        t4.status = "DRAFT";
        t4.createdByEmail = buyer1.email;
        t4.createdByName = buyer1.fullName;
        t4.persist();

        AuditLog.record(t4.id, "CREATED", buyer1.email, buyer1.fullName, "Gara creata in stato DRAFT con budget € 65000.00");

        LOG.info("[SEEDER] Dataset demo caricato con successo (5 utenti, 4 gare, 4 offerte).");
    }

    private User createUser(String email, String fullName, String role, String passwordHash) {
        User u = new User();
        u.email = email;
        u.fullName = fullName;
        u.role = role;
        u.password = passwordHash;
        u.persist();
        return u;
    }

    private void createBid(Tender tender, User supplier, BigDecimal amount, LocalDateTime submittedAt) {
        Bid b = new Bid();
        b.tender = tender;
        b.bidderEmail = supplier.email;
        b.bidderName = supplier.fullName;
        b.amount = amount;
        b.submissionDate = submittedAt;
        b.persist();
    }
}