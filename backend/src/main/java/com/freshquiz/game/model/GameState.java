package com.freshquiz.game.model;

import java.time.Instant;
import java.util.List;

public record GameState(
		String code,
		GameStatus status,
		String hostNickname,
		CurrentQuestion currentQuestion,
		int currentQuestionPosition,
		int totalQuestions,
		Instant deadlineAt,
		List<PlayerSummary> players) {
}
