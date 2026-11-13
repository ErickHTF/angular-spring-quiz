package com.freshquiz.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class GamePlayerId implements Serializable {

	@Column(name = "game_id", nullable = false)
	private String gameId;

	@Column(name = "player_id", nullable = false)
	private String playerId;
}
