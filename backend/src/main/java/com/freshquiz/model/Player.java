package com.freshquiz.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "players")
@Getter
@Setter
@NoArgsConstructor
public class Player {

	@Id
	@Column(name = "id", nullable = false)
	private String id;

	@Column(name = "nickname", nullable = false)
	private String nickname;

	@Column(name = "player_token", nullable = false, unique = true)
	private String playerToken;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Player(String id, String nickname, String playerToken, Instant createdAt) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.nickname = nickname;
		this.playerToken = playerToken != null ? playerToken : UUID.randomUUID().toString();
		this.createdAt = createdAt != null ? createdAt : Instant.now();
	}
}
