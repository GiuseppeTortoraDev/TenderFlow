package com.tenderflow.listener;

import com.tenderflow.model.Bid;
import com.tenderflow.service.EventBroadcaster;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class BidConsumer {

    private static final Logger LOG = Logger.getLogger(BidConsumer.class);

    @Inject
    EventBroadcaster broadcaster;

    @Incoming("bid-events-in")
    public void consume(Bid bid) {
        Long tenderId = bid.tender != null ? bid.tender.id : null;

        LOG.infof(" [KAFKA EVENT] Nuova offerta ricevuta! Offerente: %s | Importo: %s € | Gara ID: %d",
                bid.bidderName, bid.amount, tenderId);

        broadcaster.publish(new EventBroadcaster.LiveEvent(
                "BID_EVENT",
                tenderId,
                bid.bidderName,
                "Nuova offerta di € " + bid.amount + " da " + bid.bidderName + " (Gara #" + tenderId + ")"
        ));
    }
}