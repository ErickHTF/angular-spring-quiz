package com.freshquiz.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
class GameControllerTest {

	@Autowired
	MockMvc mvc;

	private MvcResult createGame() throws Exception {
		return mvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"Host\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").isString())
				.andReturn();
	}

	private static String code(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
	}

	@Test
	void hostAndPlayerFlow() throws Exception {
		MvcResult created = createGame();
		String code = code(created);
		Cookie host = created.getResponse().getCookie("fresh_host_" + code);

		MvcResult joined = mvc.perform(post("/api/games/" + code.toLowerCase() + "/join")
				.contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"Ana\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.playerId").isString())
				.andExpect(cookie().httpOnly("fresh_player_" + code, true))
				.andReturn();
		Cookie player = joined.getResponse().getCookie("fresh_player_" + code);

		mvc.perform(get("/api/games/" + code + "/me").cookie(player))
				.andExpect(jsonPath("$.role").value("player"))
				.andExpect(jsonPath("$.nickname").value("Ana"));
		mvc.perform(get("/api/games/" + code + "/me").cookie(host))
				.andExpect(jsonPath("$.role").value("host"))
				.andExpect(jsonPath("$.nickname").value("Host"));

		mvc.perform(post("/api/games/" + code + "/start").cookie(player))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Apenas o host pode iniciar a partida."));
		mvc.perform(post("/api/games/" + code + "/start").cookie(host))
				.andExpect(status().isOk());

		mvc.perform(get("/api/games/" + code + "/state").cookie(player))
				.andExpect(jsonPath("$.status").value("question"))
				.andExpect(jsonPath("$.currentQuestion.correctChoiceId").doesNotExist())
				.andExpect(jsonPath("$.currentQuestion.answerCounts").doesNotExist())
				.andExpect(jsonPath("$.deadlineAt").isString());

		mvc.perform(post("/api/games/" + code + "/answer").cookie(player)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnprocessableEntity());
		mvc.perform(post("/api/games/" + code + "/answer").cookie(player)
				.contentType(MediaType.APPLICATION_JSON).content("{\"choiceId\":\"nao-existe\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Alternativa inválida."));

		mvc.perform(post("/api/games/" + code + "/skip").cookie(host)).andExpect(status().isOk());
		mvc.perform(post("/api/games/" + code + "/finish").cookie(host)).andExpect(status().isOk());

		mvc.perform(post("/api/games/" + code + "/leave"))
				.andExpect(cookie().maxAge("fresh_player_" + code, 0));
	}

	@Test
	void stateRequiresSession() throws Exception {
		String code = code(createGame());
		mvc.perform(get("/api/games/" + code + "/state"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Não autorizado."));
		mvc.perform(get("/api/games/" + code + "/events")).andExpect(status().isUnauthorized());
	}

	@Test
	void invalidJsonReturnsFriendlyError() throws Exception {
		mvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("JSON inválido."));
	}

	@Test
	void healthChecksDatabase() throws Exception {
		mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
	}
}
