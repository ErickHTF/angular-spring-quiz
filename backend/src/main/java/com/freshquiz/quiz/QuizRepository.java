package com.freshquiz.quiz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class QuizRepository {

	public record QuizRow(String id, String title, int questionCount, String ownerTokenHash) {
	}

	public record QuestionRow(String id, String quizId, int position, String prompt, int durationSeconds) {
	}

	public record ChoiceRow(String id, String questionId, int position, String label, boolean isCorrect) {
	}

	private final JdbcClient jdbc;

	public QuizRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public static String createId() {
		return UUID.randomUUID().toString();
	}

	public void insertQuiz(String id, String title, String ownerTokenHash) {
		jdbc.sql("INSERT INTO quizzes (id, title, owner_token_hash) VALUES (:id, :title, :ownerTokenHash)")
				.param("id", id)
				.param("title", title)
				.param("ownerTokenHash", ownerTokenHash)
				.update();
	}

	public void updateQuizTitle(String id, String title) {
		jdbc.sql("UPDATE quizzes SET title = :title WHERE id = :id")
				.param("id", id)
				.param("title", title)
				.update();
	}

	public void deleteQuestions(String quizId) {
		jdbc.sql("DELETE FROM questions WHERE quiz_id = :quizId")
				.param("quizId", quizId)
				.update();
	}

	public void deleteQuiz(String quizId) {
		jdbc.sql("DELETE FROM quizzes WHERE id = :quizId")
				.param("quizId", quizId)
				.update();
	}

	public void insertQuestion(String id, String quizId, int position, String prompt, int durationSeconds) {
		jdbc.sql("""
				INSERT INTO questions (id, quiz_id, position, prompt, duration_seconds)
				VALUES (:id, :quizId, :position, :prompt, :durationSeconds)
				""")
				.param("id", id)
				.param("quizId", quizId)
				.param("position", position)
				.param("prompt", prompt)
				.param("durationSeconds", durationSeconds)
				.update();
	}

	public void insertChoice(String id, String questionId, int position, String label, boolean isCorrect) {
		jdbc.sql("""
				INSERT INTO choices (id, question_id, position, label, is_correct)
				VALUES (:id, :questionId, :position, :label, :isCorrect)
				""")
				.param("id", id)
				.param("questionId", questionId)
				.param("position", position)
				.param("label", label)
				.param("isCorrect", isCorrect)
				.update();
	}

	public boolean exists(String quizId) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM quizzes WHERE id = :quizId)")
				.param("quizId", quizId)
				.query(Boolean.class)
				.single();
	}

	public boolean hasGames(String quizId) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM games WHERE quiz_id = :quizId)")
				.param("quizId", quizId)
				.query(Boolean.class)
				.single();
	}

	public Optional<String> findOwnerTokenHash(String quizId) {
		return jdbc.sql("SELECT owner_token_hash FROM quizzes WHERE id = :quizId")
				.param("quizId", quizId)
				.query(String.class)
				.optional();
	}

	public Optional<String> findTitle(String quizId) {
		return jdbc.sql("SELECT title FROM quizzes WHERE id = :quizId")
				.param("quizId", quizId)
				.query(String.class)
				.optional();
	}

	public List<QuestionRow> findQuestions(String quizId) {
		return jdbc.sql("""
				SELECT id, quiz_id, position, prompt, duration_seconds
				FROM questions
				WHERE quiz_id = :quizId
				ORDER BY position ASC
				""")
				.param("quizId", quizId)
				.query((rs, i) -> new QuestionRow(
						rs.getString("id"),
						rs.getString("quiz_id"),
						rs.getInt("position"),
						rs.getString("prompt"),
						rs.getInt("duration_seconds")))
				.list();
	}

	public List<ChoiceRow> findChoices(List<String> questionIds) {
		if (questionIds == null || questionIds.isEmpty()) return List.of();
		return jdbc.sql("""
				SELECT id, question_id, position, label, is_correct
				FROM choices
				WHERE question_id IN (:ids)
				ORDER BY position ASC
				""")
				.param("ids", questionIds)
				.query((rs, i) -> new ChoiceRow(
						rs.getString("id"),
						rs.getString("question_id"),
						rs.getInt("position"),
						rs.getString("label"),
						rs.getBoolean("is_correct")))
				.list();
	}

	public List<QuizRow> listQuizRows() {
		return jdbc.sql("""
				SELECT q.id, q.title, count(qs.id)::int AS question_count, q.owner_token_hash
				FROM quizzes q
				LEFT JOIN questions qs ON qs.quiz_id = q.id
				GROUP BY q.id, q.title, q.owner_token_hash, q.created_at
				ORDER BY q.created_at, q.id
				""")
				.query((rs, i) -> new QuizRow(
						rs.getString("id"),
						rs.getString("title"),
						rs.getInt("question_count"),
						rs.getString("owner_token_hash")))
				.list();
	}
}
