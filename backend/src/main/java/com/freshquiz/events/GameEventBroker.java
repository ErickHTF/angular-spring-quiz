package com.freshquiz.events;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.freshquiz.game.GameService;
import com.freshquiz.game.model.GameState;
import com.freshquiz.game.model.Viewer;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Mantém as conexões SSE abertas por sala e envia o estado atualizado a cada mudança.
 * O estado é calculado por conexão porque host e jogadores enxergam votos diferentes.
 */
@Component
public class GameEventBroker {

	private record Subscriber(SseEmitter emitter, Viewer viewer) {
	}

	private final Map<String, Set<Subscriber>> connections = new ConcurrentHashMap<>();
	private final GameService games;

	public GameEventBroker(GameService games) {
		this.games = games;
	}

	public SseEmitter subscribe(String code, Viewer viewer) {
		// 0 = sem timeout: o stream fica aberto a partida inteira, ignorando o timeout assíncrono padrão.
		SseEmitter emitter = new SseEmitter(0L);
		Subscriber subscriber = new Subscriber(emitter, viewer);
		connections.computeIfAbsent(code, key -> ConcurrentHashMap.newKeySet()).add(subscriber);

		Runnable unsubscribe = () -> connections.computeIfPresent(code, (key, set) -> {
			set.remove(subscriber);
			return set.isEmpty() ? null : set;
		});
		emitter.onCompletion(unsubscribe);
		emitter.onTimeout(unsubscribe);
		emitter.onError(error -> unsubscribe.run());

		sendState(code, subscriber);
		return emitter;
	}

	@EventListener
	public void onGameChanged(GameChangedEvent event) {
		Set<Subscriber> subscribers = connections.get(event.code());
		if (subscribers == null) return;
		for (Subscriber subscriber : subscribers) {
			sendState(event.code(), subscriber);
		}
	}

	/**
	 * Comentário SSE periódico: mantém a conexão viva em proxies que fecham conexões ociosas e é
	 * o que descobre clientes que sumiram. Uma escrita para um cliente morto costuma falhar só no
	 * segundo ping (a primeira ainda cabe no buffer TCP), então a limpeza leva até ~50s.
	 */
	@Scheduled(fixedRate = 25_000)
	public void heartbeat() {
		connections.values().forEach(subscribers -> subscribers.forEach(subscriber -> {
			try {
				subscriber.emitter().send(SseEmitter.event().comment("ping"));
			}
			catch (IOException | IllegalStateException closed) {
				subscriber.emitter().completeWithError(closed);
			}
		}));
	}

	private void sendState(String code, Subscriber subscriber) {
		try {
			GameState state = games.getState(code, subscriber.viewer()).orElse(null);
			if (state != null) {
				subscriber.emitter().send(SseEmitter.event().name("state").data(state));
			}
		}
		catch (IOException | IllegalStateException closed) {
			// O cliente fechou a conexão; o Spring dispara o onError/onCompletion, que remove o assinante.
			subscriber.emitter().completeWithError(closed);
		}
	}
}
