package com.freshquiz.game;

import java.time.Duration;
import java.time.Instant;

public final class Scoring {

	private Scoring() {
	}

	/** 1000 pontos por acerto mais até 500 de bônus proporcional à rapidez. */
	public static int calculatePoints(int durationSeconds, Instant startedAt, Instant answeredAt) {
		double elapsedSeconds = Math.max(0, Duration.between(startedAt, answeredAt).toMillis() / 1000.0);
		long speedBonus = Math.max(0, Math.round(500 * (1 - elapsedSeconds / durationSeconds)));
		return 1000 + (int) speedBonus;
	}

	public static boolean isDeadlineExpired(Instant deadlineAt, Instant now) {
		return now.isAfter(deadlineAt);
	}
}
