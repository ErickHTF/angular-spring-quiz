package com.freshquiz.quiz.model;

public record QuizSummary(String id, String title, int questionCount, boolean canEdit) {

	public QuizSummary(String id, String title, int questionCount) {
		this(id, title, questionCount, false);
	}
}
