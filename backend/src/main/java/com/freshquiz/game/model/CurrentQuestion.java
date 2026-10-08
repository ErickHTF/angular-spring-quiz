package com.freshquiz.game.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

public record CurrentQuestion(
		String id,
		int position,
		String prompt,
		int durationSeconds,
		List<Choice> choices,
		String correctChoiceId,
		@JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Integer> answerCounts) {
}
