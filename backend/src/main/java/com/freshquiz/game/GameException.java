package com.freshquiz.game;

/** Erro de regra de negócio; a mensagem é exibida ao usuário. */
public class GameException extends RuntimeException {

	public GameException(String message) {
		super(message);
	}
}
