package com.freshquiz.game;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.freshquiz.model.GamePlayer;
import com.freshquiz.model.GamePlayerId;

@Repository
public interface GamePlayerJpaRepository extends JpaRepository<GamePlayer, GamePlayerId> {

	List<GamePlayer> findByGameIdOrderByScoreDescJoinedAtAsc(String gameId);

	Optional<GamePlayer> findByGameIdAndPlayerId(String gameId, String playerId);

	@org.springframework.data.jpa.repository.Query("SELECT gp FROM GamePlayer gp WHERE gp.game.id = :gameId AND LOWER(gp.player.nickname) = LOWER(:nickname)")
	Optional<GamePlayer> findByGameIdAndPlayerNicknameIgnoreCase(@org.springframework.data.repository.query.Param("gameId") String gameId, @org.springframework.data.repository.query.Param("nickname") String nickname);
}
