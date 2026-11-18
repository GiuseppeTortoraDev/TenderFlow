package com.tenderflow.scheduler;

import com.tenderflow.service.TenderLifecycleService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TenderExpirationScheduler {

    private static final Logger LOG = Logger.getLogger(TenderExpirationScheduler.class);

    @Inject
    TenderLifecycleService lifecycleService;

    // Esegue ogni giorno a mezzanotte (e all'avvio dopo 30s per allineare eventuali gare già scadute)
    @Scheduled(cron = "0 0 0 * * ?")
    public void scheduledExpirationCheck() {
        int closedCount = lifecycleService.closeExpiredTenders();
        if (closedCount > 0) {
            LOG.infof("[SCHEDULER] Chiuse automaticamente %d gare scadute.", closedCount);
        }
    }
}