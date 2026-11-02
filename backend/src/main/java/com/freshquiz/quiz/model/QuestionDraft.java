package com.freshquiz.quiz.model;

import java.util.List;

public record QuestionDraft(String prompt, int durationSeconds, List<ChoiceDraft> choices) {
}
