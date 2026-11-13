package com.freshquiz.quiz;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.freshquiz.model.Quiz;

@Repository
public interface QuizJpaRepository extends JpaRepository<Quiz, String> {

	Optional<Quiz> findById(String id);
}
