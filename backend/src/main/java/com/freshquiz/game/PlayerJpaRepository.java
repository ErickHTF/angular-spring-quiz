package com.freshquiz.game;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.freshquiz.model.Player;

@Repository
public interface PlayerJpaRepository extends JpaRepository<Player, String> {

	Optional<Player> findByPlayerToken(String playerToken);
}
