package com.freshquiz.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
public class Question {

	@Id
	@Column(name = "id", nullable = false)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "quiz_id", nullable = false)
	private Quiz quiz;

	@Column(name = "position", nullable = false)
	private int position;

	@Column(name = "prompt", nullable = false)
	private String prompt;

	@Column(name = "duration_seconds", nullable = false)
	private int durationSeconds;

	@OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("position ASC")
	private List<Choice> choices = new ArrayList<>();

	public Question(String id, Quiz quiz, int position, String prompt, int durationSeconds) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.quiz = quiz;
		this.position = position;
		this.prompt = prompt;
		this.durationSeconds = durationSeconds;
	}

	public void update(String prompt, int durationSeconds) {
		this.prompt = prompt;
		this.durationSeconds = durationSeconds;
	}

	public Choice addChoice(String label, boolean isCorrect) {
		int nextPos = choices.size() + 1;
		Choice choice = new Choice(UUID.randomUUID().toString(), this, nextPos, label, isCorrect);
		choices.add(choice);
		return choice;
	}

	public void removeChoice(String choiceId) {
		choices.removeIf(c -> c.getId().equals(choiceId));
		for (int i = 0; i < choices.size(); i++) {
			choices.get(i).setPosition(i + 1);
		}
	}

	public void setCorrectChoice(String choiceId) {
		for (Choice c : choices) {
			c.setCorrect(c.getId().equals(choiceId));
		}
	}
}
