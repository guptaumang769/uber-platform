package com.umang.uber.notification.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Holds the live SSE connections per tripId and fans events out to them.
 *
 * <p>WHY SSE (not WebSocket): the live map is a ONE-WAY, server-to-browser stream (trip status +
 * the driver's moving position). SSE is a plain HTTP/1.1 text stream — no upgrade handshake, no
 * extra sub-protocol, and browsers auto-reconnect via EventSource. WebSocket's bidirectional,
 * binary-capable channel would be over-engineered here; the rider never pushes anything back on
 * this channel. SSE keeps it simple and robust behind ordinary HTTP infrastructure.
 *
 * <p>A CopyOnWriteArrayList per trip makes concurrent add (new subscriber) vs iterate (fan-out
 * from the Kafka consumer thread) safe without explicit locking.
 */
@Slf4j
@Component
public class TripStreamRegistry {

    private final Map<Long, List<SseEmitter>> emittersByTrip = new java.util.concurrent.ConcurrentHashMap<>();

    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 min

    /** Register a new browser subscription for a trip's stream. */
    public SseEmitter subscribe(Long tripId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emittersByTrip.computeIfAbsent(tripId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        // Clean up on completion/timeout/error so we don't leak dead emitters.
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));
        log.debug("New SSE subscriber for trip {} ({} total)", tripId, list.size());
        return emitter;
    }

    /** Push a named event to every subscriber of a trip. Dead emitters are dropped. */
    public void publish(Long tripId, String eventName, Object payload) {
        sendTo(emittersByTrip.get(tripId), eventName, payload);
    }

    /** Push a named event to EVERY open stream (used for non-trip-scoped location pings). */
    public void broadcast(String eventName, Object payload) {
        for (List<SseEmitter> list : emittersByTrip.values()) {
            sendTo(list, eventName, payload);
        }
    }

    private void sendTo(List<SseEmitter> list, String eventName, Object payload) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException e) {
                // Client went away — complete and let the callback remove it.
                emitter.completeWithError(e);
            }
        }
    }
}
