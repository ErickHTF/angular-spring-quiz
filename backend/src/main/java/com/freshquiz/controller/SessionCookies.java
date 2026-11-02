package com.freshquiz.controller;

import java.time.Duration;
import java.util.Arrays;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public final class SessionCookies {

	private static final Duration MAX_AGE = Duration.ofDays(1);
	private static final Duration QUIZ_COOKIE_MAX_AGE = Duration.ofDays(30);

	private SessionCookies() {
	}

	public static String hostName(String code) {
		return "fresh_host_" + code;
	}

	public static String playerName(String code) {
		return "fresh_player_" + code;
	}

	public static String quizName(String quizId) {
		return "fresh_quiz_" + quizId;
	}

	public static String get(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) return null;
		return Arrays.stream(cookies)
				.filter(cookie -> cookie.getName().equals(name))
				.map(Cookie::getValue)
				.findFirst()
				.orElse(null);
	}

	public static void set(HttpHeaders headers, String name, String value) {
		headers.add(HttpHeaders.SET_COOKIE, build(name, value, MAX_AGE).toString());
	}

	public static void setQuiz(HttpHeaders headers, String quizId, String token) {
		headers.add(HttpHeaders.SET_COOKIE, build(quizName(quizId), token, QUIZ_COOKIE_MAX_AGE).toString());
	}

	public static void clear(HttpHeaders headers, String code) {
		for (String name : new String[] { hostName(code), playerName(code) }) {
			headers.add(HttpHeaders.SET_COOKIE, build(name, "", Duration.ZERO).toString());
		}
	}

	public static void clearQuiz(HttpHeaders headers, String quizId) {
		headers.add(HttpHeaders.SET_COOKIE, build(quizName(quizId), "", Duration.ZERO).toString());
	}

	private static ResponseCookie build(String name, String value, Duration maxAge) {
		return ResponseCookie.from(name, value)
				.path("/")
				.httpOnly(true)
				.sameSite("Lax")
				.maxAge(maxAge)
				.build();
	}
}
