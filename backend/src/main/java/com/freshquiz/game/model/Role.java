package com.freshquiz.game.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
	HOST, PLAYER;

	@JsonValue
	public String value() {
		return name().toLowerCase();
	}
}
