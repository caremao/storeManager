package com.caremao.storemanager.events;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.caremao.storemanager.auth.Actor;

@RestController
public class EventsController {

    private final EventBroadcaster broadcaster;

    public EventsController(EventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(Actor actor) {
        return broadcaster.subscribe(actor);
    }
}
