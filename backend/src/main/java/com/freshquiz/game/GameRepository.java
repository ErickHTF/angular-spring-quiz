package com.freshquiz.game;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.freshquiz.game.model.GameStatus;
import com.freshquiz.game.model.PlayerSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GameRepository {

	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final JdbcClient jdbc;

	public GameRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public record GameRow(
			String id,
			String quizId,
			String code,
			String hostToken,
			String hostNickname,
			GameStatus status,
			int currentQuestionPosition,
			Instant questionStartedAt,
			Instant questionDeadlineAt) {
	}

	public record QuestionRow(String id, int position, String prompt, int durationSeconds) {
	}

	public record ChoiceRow(String id, int position, String label, boolean isCorrect) {
	}

	public record PlayerRow(String id, String nickname) {
	}

	public static String createToken() {
		return UUID.randomUUID().toString();
	}

	public static String createCode() {
		StringBuilder code = new StringBuilder(6);
		for (int i = 0; i < 6; i++) {
			code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
		}
		return code.toString();
	}

	static OffsetDateTime toTimestamp(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	public Optional<String> firstQuizId() {
		return jdbc.sql("SELECT id FROM quizzes ORDER BY created_at LIMIT 1")
				.query(String.class)
				.optional();
	}

	public Optional<GameRow> getGame(String code) {
		return jdbc.sql("""
				SELECT id, quiz_id, code, host_token, host_nickname, status,
				  current_question_position, question_started_at, question_deadline_at
				FROM games
				WHERE code = :code
				""")
				.param("code", code)
				.query((rs, i) -> new GameRow(
						rs.getString("id"),
						rs.getString("quiz_id"),
						rs.getString("code"),
						rs.getString("host_token"),
						rs.getString("host_nickname"),
						GameStatus.fromValue(rs.getString("status")),
						rs.getInt("current_question_position"),
						instant(rs, "question_started_at"),
						instant(rs, "question_deadline_at")))
				.optional();
	}

	public Optional<QuestionRow> getQuestion(String quizId, int position) {
		return jdbc.sql("""
				SELECT id, position, prompt, duration_seconds
				FROM questions
				WHERE quiz_id = :quizId AND position = :position
				""")
				.param("quizId", quizId)
				.param("position", position)
				.query((rs, i) -> new QuestionRow(
						rs.getString("id"),
						rs.getInt("position"),
						rs.getString("prompt"),
						rs.getInt("duration_seconds")))
				.optional();
	}

	public List<ChoiceRow> getQuestionChoices(String questionId) {
		return jdbc.sql("""
				SELECT id, position, label, is_correct
				FROM choices
				WHERE question_id = :questionId
				ORDER BY position
				""")
				.param("questionId", questionId)
				.query((rs, i) -> new ChoiceRow(
						rs.getString("id"),
						rs.getInt("position"),
						rs.getString("label"),
						rs.getBoolean("is_correct")))
				.list();
	}

	public int countQuestions(String quizId) {
		return jdbc.sql("SELECT count(*)::int FROM questions WHERE quiz_id = :quizId")
				.param("quizId", quizId)
				.query(Integer.class)
				.single();
	}

	public Optional<PlayerRow> getPlayerByToken(String gameId, String playerToken) {
		return jdbc.sql("""
				SELECT p.id, p.nickname
				FROM players p
				JOIN game_players gp ON gp.player_id = p.id
				WHERE gp.game_id = :gameId AND p.player_token = :playerToken
				""")
				.param("gameId", gameId)
				.param("playerToken", playerToken)
				.query((rs, i) -> new PlayerRow(rs.getString("id"), rs.getString("nickname")))
				.optional();
	}

	public boolean hasAnsweredQuestion(String gameId, String questionId, String playerId) {
		return jdbc.sql("""
				SELECT EXISTS (
				  SELECT 1 FROM answers
				  WHERE game_id = :gameId AND question_id = :questionId
				    AND player_id = :playerId
				)
				""")
				.param("gameId", gameId)
				.param("questionId", questionId)
				.param("playerId", playerId)
				.query(Boolean.class)
				.single();
	}

	public Map<String, Integer> getAnswerCounts(String gameId, String questionId) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		jdbc.sql("""
				SELECT choice_id, count(*)::int AS count
				FROM answers
				WHERE game_id = :gameId AND question_id = :questionId
				GROUP BY choice_id
				""")
				.param("gameId", gameId)
				.param("questionId", questionId)
				.query(rs -> {
					counts.put(rs.getString("choice_id"), rs.getInt("count"));
				});
		return counts;
	}

	public List<PlayerSummary> listPlayers(GameRow game) {
		return jdbc.sql("""
				SELECT p.id, p.nickname, gp.score,
				  EXISTS (
				    SELECT 1 FROM answers a
				    WHERE a.game_id = :gameId
				      AND a.player_id = p.id
				      AND a.question_id = (
				        SELECT id FROM questions
				        WHERE quiz_id = :quizId AND position = :position
				      )
				  ) AS has_answered
				FROM game_players gp
				JOIN players p ON p.id = gp.player_id
				WHERE gp.game_id = :gameId
				ORDER BY gp.score DESC, gp.joined_at
				""")
				.param("gameId", game.id())
				.param("quizId", game.quizId())
				.param("position", game.currentQuestionPosition())
				.query((rs, i) -> new PlayerSummary(
						rs.getString("id"),
						rs.getString("nickname"),
						rs.getInt("score"),
						rs.getBoolean("has_answered")))
				.list();
	}
}
