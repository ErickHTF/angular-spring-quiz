package com.freshquiz.web;

import java.util.List;
import java.util.Map;

import com.freshquiz.quiz.QuizService;
import com.freshquiz.quiz.model.QuizCreated;
import com.freshquiz.quiz.model.QuizDetails;
import com.freshquiz.quiz.model.QuizDraft;
import com.freshquiz.quiz.model.QuizSummary;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

	private static final String QUIZ_TOKEN_HEADER = "X-Quiz-Token";
	private static final Map<String, Object> OK = Map.of("ok", true);

	private final QuizService quizzes;

	public QuizController(QuizService quizzes) {
		this.quizzes = quizzes;
	}

	@GetMapping
	public List<QuizSummary> list(HttpServletRequest request) {
		return quizzes.listQuizzes(id -> resolveToken(request, id));
	}

	@PostMapping
	public ResponseEntity<QuizSummary> create(@RequestBody QuizDraft body) {
		QuizCreated created = quizzes.createQuiz(body);
		HttpHeaders headers = new HttpHeaders();
		SessionCookies.setQuiz(headers, created.summary().id(), created.ownerToken());
		headers.add(QUIZ_TOKEN_HEADER, created.ownerToken());
		return ResponseEntity.ok().headers(headers).body(created.summary());
	}

	@GetMapping("/{id}")
	public ResponseEntity<QuizDetails> get(@PathVariable String id, HttpServletRequest request) {
		String token = resolveToken(request, id);
		return ResponseEntity.ok(quizzes.getQuiz(id, token));
	}

	@PutMapping("/{id}")
	public ResponseEntity<QuizSummary> update(
			@PathVariable String id,
			@RequestBody QuizDraft body,
			HttpServletRequest request) {
		String token = resolveToken(request, id);
		return ResponseEntity.ok(quizzes.updateQuiz(id, body, token));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Map<String, Object>> delete(@PathVariable String id, HttpServletRequest request) {
		String token = resolveToken(request, id);
		quizzes.deleteQuiz(id, token);
		HttpHeaders headers = new HttpHeaders();
		SessionCookies.clearQuiz(headers, id);
		return ResponseEntity.ok().headers(headers).body(OK);
	}

	private static String resolveToken(HttpServletRequest request, String quizId) {
		String cookie = SessionCookies.get(request, SessionCookies.quizName(quizId));
		if (cookie != null && !cookie.isBlank()) return cookie;
		String header = request.getHeader(QUIZ_TOKEN_HEADER);
		if (header != null && !header.isBlank()) return header;
		String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return auth.substring(7).trim();
		}
		return null;
	}
}
