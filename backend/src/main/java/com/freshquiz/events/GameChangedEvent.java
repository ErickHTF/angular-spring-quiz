package com.freshquiz.events;

/**
 * Publicado depois de cada ação que pode alterar uma sala (inclusive as que acabam não mudando
 * nada, como um reveal já feito). O {@link GameEventBroker} reenvia o estado aos clientes SSE dela.
 */
public record GameChangedEvent(String code) {
}
