package com.freshquiz.game;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.freshquiz.model.Answer;
import com.freshquiz.model.AnswerId;

@Repository
public interface AnswerJpaRepository extends JpaRepository<Answer, AnswerId> {

	List<Answer> findByIdGameIdAndIdQuestionId(String gameId, String questionId);
}
