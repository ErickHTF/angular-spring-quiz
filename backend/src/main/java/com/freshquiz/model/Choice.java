package com.freshquiz.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "choices")
@Getter
@Setter
@NoArgsConstructor
public class Choice {

	@Id
	@Column(name = "id", nullable = false)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "question_id", nullable = false)
	private Question question;

	@Column(name = "position", nullable = false)
	private int position;

	@Column(name = "label", nullable = false)
	private String label;

	@Column(name = "is_correct", nullable = false)
	private boolean isCorrect;

	public Choice(String id, Question question, int position, String label, boolean isCorrect) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.question = question;
		this.position = position;
		this.label = label;
		this.isCorrect = isCorrect;
	}

	public void update(String label, boolean isCorrect) {
		this.label = label;
		this.isCorrect = isCorrect;
	}
}
