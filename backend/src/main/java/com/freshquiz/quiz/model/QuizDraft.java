package com.freshquiz.quiz.model;

import java.util.List;

public record QuizDraft(String title, List<QuestionDraft> questions) {
}
