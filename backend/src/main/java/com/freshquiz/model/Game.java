package com.freshquiz.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.freshquiz.game.model.GameStatus;

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
public class Game {

	@Id
	@Column(name = "id", nullable = false)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "quiz_id", nullable = false)
	private Quiz quiz;

	@Column(name = "code", nullable = false, unique = true, columnDefinition = "CHAR(6)")
	private String code;

	@Column(name = "host_token", nullable = false)
	private String hostToken;

	@Column(name = "host_nickname")
	private String hostNickname;

	@Column(name = "status", nullable = false)
	@Convert(converter = GameStatusConverter.class)
	private GameStatus status;

	@Column(name = "current_question_position", nullable = false)
	private int currentQuestionPosition;

	@Column(name = "question_started_at")
	private Instant questionStartedAt;

	@Column(name = "question_deadline_at")
	private Instant questionDeadlineAt;

	@OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<GamePlayer> gamePlayers = new ArrayList<>();

	public Game(String id, Quiz quiz, String code, String hostToken, String hostNickname) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.quiz = quiz;
		this.code = code;
		this.hostToken = hostToken;
		this.hostNickname = hostNickname;
		this.status = GameStatus.LOBBY;
		this.currentQuestionPosition = 0;
	}

	public void start() {
		this.status = GameStatus.QUESTION;
		this.currentQuestionPosition = 1;
		this.questionStartedAt = Instant.now();
	}

	public void nextQuestion() {
		this.currentQuestionPosition++;
		this.status = GameStatus.QUESTION;
		this.questionStartedAt = Instant.now();
	}

	public void skipQuestion() {
		this.status = GameStatus.REVEAL;
		this.questionDeadlineAt = Instant.now();
	}

	public void finish() {
		this.status = GameStatus.FINISHED;
	}

	public void restart() {
		this.status = GameStatus.LOBBY;
		this.currentQuestionPosition = 0;
		this.questionStartedAt = null;
		this.questionDeadlineAt = null;
	}
}
