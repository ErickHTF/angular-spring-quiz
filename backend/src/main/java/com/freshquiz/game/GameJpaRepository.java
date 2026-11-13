package com.freshquiz.game;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.freshquiz.model.Game;

@Repository
public interface GameJpaRepository extends JpaRepository<Game, String> {

	Optional<Game> findByCode(String code);
}
