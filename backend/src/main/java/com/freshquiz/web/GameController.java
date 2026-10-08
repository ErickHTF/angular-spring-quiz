package com.freshquiz.web;

import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import com.freshquiz.events.GameChangedEvent;
import com.freshquiz.events.GameEventBroker;
import com.freshquiz.game.GameService;
import com.freshquiz.game.model.GameSession;
import com.freshquiz.game.model.GameState;
import com.freshquiz.game.model.PlayerSession;
import com.freshquiz.game.model.Role;
import com.freshquiz.game.model.Viewer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/games")
public class GameController {

	public record NicknameBody(String nickname) {
	}

	public record AnswerBody(String choiceId) {
	}

	public record MeResponse(Role role, String playerId, String nickname) {
	}

	private static final Map<String, Object> OK = Map.of("ok", true);

	private final GameService games;
	private final GameEventBroker broker;
	private final Authorizer authorizer;
	private final ApplicationEventPublisher events;

	public GameController(GameService games, GameEventBroker broker, Authorizer authorizer,
			ApplicationEventPublisher events) {
		this.games = games;
		this.broker = broker;
		this.authorizer = authorizer;
		this.events = events;
	}

	@PostMapping
	public ResponseEntity<Map<String, String>> create(@RequestBody NicknameBody body) {
		GameSession session = games.createGame(body.nickname());
		HttpHeaders headers = new HttpHeaders();
		SessionCookies.set(headers, SessionCookies.hostName(session.code()), session.hostToken());
		return ResponseEntity.ok().headers(headers).body(Map.of("code", session.code()));
	}

	@PostMapping("/{code}/join")
	public ResponseEntity<Map<String, String>> join(@PathVariable String code, @RequestBody NicknameBody body) {
		PlayerSession session = games.joinGame(normalize(code), body.nickname());
		HttpHeaders headers = new HttpHeaders();
		SessionCookies.set(headers, SessionCookies.playerName(session.code()), session.playerToken());
		notify(session.code());
		return ResponseEntity.ok().headers(headers).body(Map.of(
				"code", session.code(),
				"nickname", session.nickname(),
				"playerId", session.playerId()));
	}

	/** O Angular não lê cookies HttpOnly: este endpoint diz quem é o usuário na sala. */
	@GetMapping("/{code}/me")
	public ResponseEntity<?> me(@PathVariable String code, HttpServletRequest request) {
		code = normalize(code);
		Viewer viewer = authorizer.authorize(request, code);
		if (viewer == null) return unauthorized();
		if (viewer.role() == Role.HOST) {
			return ResponseEntity.ok(new MeResponse(Role.HOST, null, games.hostNickname(code).orElse(null)));
		}
		return games.findPlayer(code, viewer.playerToken())
				.<ResponseEntity<?>>map(player -> ResponseEntity.ok(
						new MeResponse(Role.PLAYER, player.id(), player.nickname())))
				.orElseGet(GameController::unauthorized);
	}

	@GetMapping("/{code}/state")
	public ResponseEntity<?> state(@PathVariable String code, HttpServletRequest request) {
		code = normalize(code);
		Viewer viewer = authorizer.authorize(request, code);
		if (viewer == null) return unauthorized();
		return games.getState(code, viewer)
				.<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("Sala não encontrada.")));
	}

	@GetMapping("/{code}/events")
	public ResponseEntity<?> events(@PathVariable String code, HttpServletRequest request) {
		code = normalize(code);
		Viewer viewer = authorizer.authorize(request, code);
		if (viewer == null) return unauthorized();
		SseEmitter emitter = broker.subscribe(code, viewer);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-cache")
				.header("X-Accel-Buffering", "no")
				.body(emitter);
	}

	@PostMapping("/{code}/start")
	public ResponseEntity<?> start(@PathVariable String code, HttpServletRequest request) {
		return hostAction(code, request, "Apenas o host pode iniciar a partida.", games::startGame);
	}

	@PostMapping("/{code}/skip")
	public ResponseEntity<?> skip(@PathVariable String code, HttpServletRequest request) {
		return hostAction(code, request, "Apenas o host pode pular a pergunta.", games::skipQuestion);
	}

	@PostMapping("/{code}/next")
	public ResponseEntity<?> next(@PathVariable String code, HttpServletRequest request) {
		return hostAction(code, request, "Apenas o host pode avançar a partida.", games::advanceGame);
	}

	@PostMapping("/{code}/finish")
	public ResponseEntity<?> finish(@PathVariable String code, HttpServletRequest request) {
		return hostAction(code, request, "Apenas o host pode encerrar o quiz.", games::finishGame);
	}

	@PostMapping("/{code}/restart")
	public ResponseEntity<?> restart(@PathVariable String code, HttpServletRequest request) {
		return hostAction(code, request, "Apenas o host pode reiniciar a partida.", games::restartGame);
	}

	@PostMapping("/{code}/answer")
	public ResponseEntity<?> answer(@PathVariable String code, @RequestBody AnswerBody body,
			HttpServletRequest request) {
		code = normalize(code);
		String token = SessionCookies.get(request, SessionCookies.playerName(code));
		if (!games.verifyPlayer(code, token)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("Apenas jogadores podem responder."));
		}
		if (body.choiceId() == null || body.choiceId().isBlank()) {
			return ResponseEntity.unprocessableEntity().body(new ApiError("Alternativa obrigatória."));
		}
		games.submitAnswer(code, token, body.choiceId());
		notify(code);
		return ResponseEntity.ok(OK);
	}

	@PostMapping("/{code}/leave")
	public ResponseEntity<?> leave(@PathVariable String code) {
		code = normalize(code);
		HttpHeaders headers = new HttpHeaders();
		SessionCookies.clear(headers, code);
		notify(code);
		return ResponseEntity.ok().headers(headers).body(OK);
	}

	private ResponseEntity<?> hostAction(String code, HttpServletRequest request, String forbiddenMessage,
			Consumer<String> action) {
		String normalized = normalize(code);
		if (!authorizer.isHost(request, normalized)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(forbiddenMessage));
		}
		action.accept(normalized);
		notify(normalized);
		return ResponseEntity.ok(OK);
	}

	private void notify(String code) {
		events.publishEvent(new GameChangedEvent(code));
	}

	private static String normalize(String code) {
		return code.toUpperCase(Locale.ROOT);
	}

	private static ResponseEntity<ApiError> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("Não autorizado."));
	}
}
