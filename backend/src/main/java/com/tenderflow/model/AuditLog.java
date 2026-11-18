package com.tenderflow.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends PanacheEntity {

    public Long tenderId;
    public String action; // CREATED, PUBLISHED, DOCUMENT_UPLOADED, BID_SUBMITTED, BID_UPDATED, CLOSED_MANUAL, CLOSED_SCHEDULER
    public String actorEmail;
    public String actorName;
    public String details;
    public LocalDateTime createdAt;

    public AuditLog() {
    }

    public static void record(Long tenderId, String action, String actorEmail, String actorName, String details) {
        AuditLog log = new AuditLog();
        log.tenderId = tenderId;
        log.action = action;
        log.actorEmail = actorEmail;
        log.actorName = (actorName != null && !actorName.isBlank()) ? actorName : actorEmail;
        log.details = details;
        log.createdAt = LocalDateTime.now();
        log.persist();
    }
}