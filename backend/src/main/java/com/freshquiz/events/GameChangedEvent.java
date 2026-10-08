package com.freshquiz.events;

/** Publicado sempre que o estado de uma sala muda e os clientes SSE precisam ser avisados. */
public record GameChangedEvent(String code) {
}
