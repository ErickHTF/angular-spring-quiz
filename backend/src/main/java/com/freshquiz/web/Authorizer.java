package com.freshquiz.web;

import com.freshquiz.game.GameService;
import com.freshquiz.game.model.Role;
import com.freshquiz.game.model.Viewer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class Authorizer {

	private final GameService games;

	public Authorizer(GameService games) {
		this.games = games;
	}

	/** Retorna quem está fazendo a requisição na sala, ou {@code null} se não tiver sessão. */
	public Viewer authorize(HttpServletRequest request, String code) {
		String hostToken = SessionCookies.get(request, SessionCookies.hostName(code));
		String playerToken = SessionCookies.get(request, SessionCookies.playerName(code));
		if (games.verifyHost(code, hostToken)) return new Viewer(Role.HOST, playerToken);
		if (games.verifyPlayer(code, playerToken)) return new Viewer(Role.PLAYER, playerToken);
		return null;
	}

	public boolean isHost(HttpServletRequest request, String code) {
		Viewer viewer = authorize(request, code);
		return viewer != null && viewer.role() == Role.HOST;
	}
}
