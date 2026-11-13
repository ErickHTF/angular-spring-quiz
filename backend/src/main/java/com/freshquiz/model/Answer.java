package com.freshquiz.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "answers")
@Getter
@Setter
@NoArgsConstructor
public class Answer {

	@EmbeddedId
	private AnswerId id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumns({
			@JoinColumn(name = "game_id", referencedColumnName = "game_id", insertable = false, updatable = false),
			@JoinColumn(name = "player_id", referencedColumnName = "player_id", insertable = false, updatable = false)
	})
	private GamePlayer gamePlayer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "question_id", insertable = false, updatable = false)
	private Question question;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "choice_id", nullable = false)
	private Choice choice;

	@Column(name = "is_correct", nullable = false)
	private boolean isCorrect;

	@Column(name = "points", nullable = false)
	private int points;

	@Column(name = "answered_at", nullable = false)
	private Instant answeredAt;

	public Answer(AnswerId id, GamePlayer gamePlayer, Question question, Choice choice, boolean isCorrect, int points, Instant answeredAt) {
		this.id = id;
		this.gamePlayer = gamePlayer;
		this.question = question;
		this.choice = choice;
		this.isCorrect = isCorrect;
		this.points = points;
		this.answeredAt = answeredAt != null ? answeredAt : Instant.now();
	}
}
