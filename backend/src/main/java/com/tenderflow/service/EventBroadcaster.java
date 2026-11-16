package com.tenderflow.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EventBroadcaster {

    // 1. Struttura dati immutabile che verrà serializzata in JSON verso Angular
    public record LiveEvent(
            String type,      // Es. "TENDER_EVENT", "BID_EVENT", "PING"
            Long tenderId,    // ID della gara coinvolta (null per il PING)
            String title,     // Titolo gara o nome offerente
            String message    // Messaggio leggibile da mostrare nel toast UI
    ) {}

    // 2. BroadcastProcessor di Mutiny: permette a N client di iscriversi allo stesso flusso in memoria
    private final BroadcastProcessor<LiveEvent> processor = BroadcastProcessor.create();

    // 3. Metodo chiamato dai Consumer Kafka quando arriva un messaggio dal broker
    public void publish(LiveEvent event) {
        processor.onNext(event);
    }

    // 4. Metodo chiamato da EventStreamResource per esporre lo stream reattivo
    public Multi<LiveEvent> stream() {
        return processor;
    }
}