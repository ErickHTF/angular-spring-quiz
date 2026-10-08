package com.freshquiz.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ScoringTest {

	private final Instant start = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	void instantAnswerGetsFullBonus() {
		assertThat(Scoring.calculatePoints(20, start, start)).isEqualTo(1500);
	}

	@Test
	void halfTimeGetsHalfBonus() {
		assertThat(Scoring.calculatePoints(20, start, start.plusSeconds(10))).isEqualTo(1250);
	}

	@Test
	void lateAnswerNeverGoesBelowBase() {
		assertThat(Scoring.calculatePoints(20, start, start.plusSeconds(30))).isEqualTo(1000);
	}

	@Test
	void answerBeforeStartIsClampedToFullBonus() {
		assertThat(Scoring.calculatePoints(20, start, start.minusSeconds(1))).isEqualTo(1500);
	}

	@Test
	void deadlineExpiresOnlyAfterTheInstant() {
		assertThat(Scoring.isDeadlineExpired(start, start)).isFalse();
		assertThat(Scoring.isDeadlineExpired(start, start.plusMillis(1))).isTrue();
	}
}
