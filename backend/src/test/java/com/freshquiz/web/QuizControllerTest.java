package com.freshquiz.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freshquiz.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class QuizControllerTest {

	private static final String VALID_QUIZ = """
			{"title":"Quiz do Host","questions":[
			  {"prompt":"Qual a capital do Brasil?","durationSeconds":15,"choices":[
			    {"label":"Brasília","isCorrect":true},
			    {"label":"São Paulo","isCorrect":false}]}]}
			""";

	@Autowired
	MockMvc mvc;

	private record CreatedResult(String id, Cookie cookie, String token) {
	}

	private CreatedResult createQuiz() throws Exception {
		MvcResult result = mvc.perform(post("/api/quizzes").contentType(MediaType.APPLICATION_JSON).content(VALID_QUIZ))
				.andExpect(status().isOk())
				.andExpect(header().exists("Set-Cookie"))
				.andExpect(header().exists("X-Quiz-Token"))
				.andExpect(jsonPath("$.id").isString())
				.andExpect(jsonPath("$.title").value("Quiz do Host"))
				.andExpect(jsonPath("$.questionCount").value(1))
				.andExpect(jsonPath("$.canEdit").value(true))
				.andReturn();
		String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		Cookie cookie = result.getResponse().getCookie("fresh_quiz_" + id);
		String token = result.getResponse().getHeader("X-Quiz-Token");
		return new CreatedResult(id, cookie, token);
	}

	@Test
	void createsQuizAndListsIt() throws Exception {
		CreatedResult created = createQuiz();

		mvc.perform(get("/api/quizzes").cookie(created.cookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].title").value("Fundamentos da Web"))
				.andExpect(jsonPath("$[?(@.id == '" + created.id() + "')].canEdit").value(true));
	}

	@Test
	void retrievesQuizDetails() throws Exception {
		CreatedResult created = createQuiz();

		mvc.perform(get("/api/quizzes/" + created.id()).cookie(created.cookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(created.id()))
				.andExpect(jsonPath("$.title").value("Quiz do Host"))
				.andExpect(jsonPath("$.canEdit").value(true))
				.andExpect(jsonPath("$.questions[0].prompt").value("Qual a capital do Brasil?"));

		mvc.perform(get("/api/quizzes/" + created.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canEdit").value(false));
	}

	@Test
	void updatesAndDeletesQuizWithToken() throws Exception {
		CreatedResult created = createQuiz();

		String updatedBody = """
				{"title":"Quiz Editado","questions":[
				  {"prompt":"Nova Pergunta?","durationSeconds":20,"choices":[
				    {"label":"Opção 1","isCorrect":true},
				    {"label":"Opção 2","isCorrect":false}]}]}
				""";

		mvc.perform(put("/api/quizzes/" + created.id()).contentType(MediaType.APPLICATION_JSON).content(updatedBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Você não tem permissão para editar este quiz."));

		mvc.perform(put("/api/quizzes/" + created.id()).contentType(MediaType.APPLICATION_JSON).content(updatedBody).cookie(created.cookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Quiz Editado"));

		mvc.perform(delete("/api/quizzes/" + created.id()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Você não tem permissão para excluir este quiz."));

		MvcResult deleteResult = mvc.perform(delete("/api/quizzes/" + created.id()).cookie(created.cookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true))
				.andReturn();

		Cookie deletedCookie = deleteResult.getResponse().getCookie("fresh_quiz_" + created.id());
		assertThat(deletedCookie).isNotNull();
		assertThat(deletedCookie.getMaxAge()).isZero();
	}

	@Test
	void hostPlaysTheCustomQuiz() throws Exception {
		CreatedResult created = createQuiz();

		MvcResult sessionResult = mvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\":\"Host\",\"quizId\":\"" + created.id() + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String code = JsonPath.read(sessionResult.getResponse().getContentAsString(), "$.code");
		Cookie host = sessionResult.getResponse().getCookie("fresh_host_" + code);

		mvc.perform(post("/api/games/" + code + "/start").cookie(host)).andExpect(status().isOk());
		mvc.perform(get("/api/games/" + code + "/state").cookie(host))
				.andExpect(jsonPath("$.totalQuestions").value(1))
				.andExpect(jsonPath("$.currentQuestion.prompt").value("Qual a capital do Brasil?"));
	}

	@Test
	void rejectsInvalidQuizWithFriendlyError() throws Exception {
		mvc.perform(post("/api/quizzes").contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Quiz\",\"questions\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Adicione ao menos uma pergunta."));
		mvc.perform(post("/api/quizzes").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("JSON inválido."));
	}

	@Test
	void rejectsUnknownQuizWhenCreatingGame() throws Exception {
		mvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\":\"Host\",\"quizId\":\"nao-existe\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Quiz não encontrado."));
	}
}
