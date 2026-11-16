package com.tenderflow.controller;

import com.tenderflow.service.EventBroadcaster;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.time.Duration;

@Path("/api/events")
@Tag(name = "Real-Time Events", description = "Stream SSE degli eventi Kafka per gare e offerte")
public class EventStreamResource {

    @Inject
    EventBroadcaster broadcaster;

    @GET
    @Path("/stream")
    @PermitAll
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(summary = "Stream eventi real-time", description = "Canale Server-Sent Events alimentato da Apache Kafka.")
    public Multi<EventBroadcaster.LiveEvent> streamEvents() {
        // 1. Stream di keep-alive: emette un evento PING ogni 25 secondi
        Multi<EventBroadcaster.LiveEvent> heartbeat = Multi.createFrom()
                .ticks()
                .every(Duration.ofSeconds(25))
                .map(tick -> new EventBroadcaster.LiveEvent("PING", null, null, "keep-alive"));

        // 2. Unisce in parallelo gli eventi reali di Kafka con il battito cardiaco (heartbeat)
        return Multi.createBy().merging().streams(broadcaster.stream(), heartbeat);
    }
}