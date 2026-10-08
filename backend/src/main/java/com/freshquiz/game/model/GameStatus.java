package com.freshquiz.game.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum GameStatus {
	LOBBY, QUESTION, REVEAL, FINISHED;

	@JsonValue
	public String value() {
		return name().toLowerCase();
	}

	public static GameStatus fromValue(String value) {
		return valueOf(value.toUpperCase());
	}
}
