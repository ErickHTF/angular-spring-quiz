package com.freshquiz.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_players")
@Getter
@Setter
@NoArgsConstructor
public class GamePlayer {

	@EmbeddedId
	private GamePlayerId id;

	@ManyToOne(fetch = FetchType.LAZY)
	@MapsId("gameId")
	@JoinColumn(name = "game_id", nullable = false)
	private Game game;

	@ManyToOne(fetch = FetchType.LAZY)
	@MapsId("playerId")
	@JoinColumn(name = "player_id", nullable = false)
	private Player player;

	@Column(name = "score", nullable = false)
	private int score;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	@OneToMany(mappedBy = "gamePlayer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Answer> answers = new ArrayList<>();

	public GamePlayer(Game game, Player player) {
		this.game = game;
		this.player = player;
		this.id = new GamePlayerId(game != null ? game.getId() : null, player != null ? player.getId() : null);
		this.score = 0;
		this.joinedAt = Instant.now();
	}

	public void addScore(int points) {
		this.score += points;
	}

	public void resetScore() {
		this.score = 0;
	}
}
