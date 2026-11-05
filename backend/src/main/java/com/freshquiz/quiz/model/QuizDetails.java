package com.freshquiz.quiz.model;

import java.util.List;

public record QuizDetails(String id, String title, List<QuestionDraft> questions, boolean canEdit) {
}
