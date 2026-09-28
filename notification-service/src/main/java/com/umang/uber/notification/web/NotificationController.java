package com.umang.uber.notification.web;

import com.umang.uber.notification.service.TripStreamRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final TripStreamRegistry registry;

    /**
     * Open a live stream for a trip. The browser connects with an EventSource to this URL and
     * receives "status" (trip lifecycle) and "location" (driver position) events as they happen —
     * this is what drives the live map.
     */
    @GetMapping(value = "/trips/{tripId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long tripId) {
        return registry.subscribe(tripId);
    }
}
