package com.tenderflow.service;

import com.tenderflow.model.Bid;
import com.tenderflow.model.Tender;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class TenderLifecycleService {

    private static final Logger LOG = Logger.getLogger(TenderLifecycleService.class);

    @Channel("tender-events-out")
    Emitter<Tender> tenderEmitter;

    @Transactional
    public Bid closeAndAwardTender(Tender tender) {
        tender.status = "CLOSED";

        Bid winningBid = Bid.find("tender.id = ?1 order by amount asc", tender.id).firstResult();
        if (winningBid != null) {
            tender.winningBidderName = winningBid.bidderName;
            tender.winningAmount = winningBid.amount;
        }

        tenderEmitter.send(tender);
        return winningBid;
    }

    @Transactional
    public int closeExpiredTenders() {
        LocalDate today = LocalDate.now();
        List<Tender> expiredTenders = Tender.list("status = 'OPEN' and closingDate is not null and closingDate < ?1", today);

        for (Tender tender : expiredTenders) {
            Bid winner = closeAndAwardTender(tender);
            if (winner != null) {
                LOG.infof("[SCHEDULER] Gara #%d ('%s') scaduta e aggiudicata automaticamente a %s (€ %s)",
                        tender.id, tender.title, winner.bidderName, winner.amount);
            } else {
                LOG.infof("[SCHEDULER] Gara #%d ('%s') scaduta e chiusa automaticamente senza offerte.",
                        tender.id, tender.title);
            }
        }

        return expiredTenders.size();
    }
}