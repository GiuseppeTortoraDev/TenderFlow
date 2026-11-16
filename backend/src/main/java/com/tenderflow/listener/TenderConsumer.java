package com.tenderflow.listener;

import com.tenderflow.model.Tender;
import com.tenderflow.service.EventBroadcaster;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TenderConsumer {

    private static final Logger LOG = Logger.getLogger(TenderConsumer.class);

    @Inject
    EventBroadcaster broadcaster;

    @Incoming("tender-events-in")
    public void consume(Tender tender) {
        LOG.infof(" [Kafka Consumer] Ricevuto nuovo evento per la gara: '%s' (Stato: %s) con budget: %.2f",
                tender.title, tender.status, tender.budget);

        String action = "CLOSED".equalsIgnoreCase(tender.status)
                ? "Gara chiusa e aggiudicata"
                : "Nuova gara pubblicata";

        broadcaster.publish(new EventBroadcaster.LiveEvent(
                "TENDER_EVENT",
                tender.id,
                tender.title,
                action + ": " + tender.title
        ));
    }
}