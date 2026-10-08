package com.freshquiz.web;

import com.freshquiz.game.GameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(GameException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError gameError(GameException error) {
		return new ApiError(error.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError invalidJson() {
		return new ApiError("JSON inválido.");
	}

	@ExceptionHandler(RuntimeException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError unexpected(RuntimeException error) {
		log.error("Erro inesperado", error);
		return new ApiError("Erro inesperado.");
	}
}
