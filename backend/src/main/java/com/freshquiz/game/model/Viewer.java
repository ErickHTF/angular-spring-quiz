package com.freshquiz.game.model;

/** Quem está olhando o estado: define se as contagens de votos podem ser exibidas. */
public record Viewer(Role role, String playerToken) {
}
