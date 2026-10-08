import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const gamesCreated = new Counter("games_created");
const playersJoined = new Counter("players_joined");
const answersSubmitted = new Counter("answers_submitted");
const successRate = new Rate("success_rate");
const responseTime = new Trend("response_time", true);

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export const options = {
  stages: [
    { duration: "10s", target: 50 },
    { duration: "30s", target: 50 },
    { duration: "10s", target: 100 },
    { duration: "30s", target: 100 },
    { duration: "10s", target: 0 },
  ],
  thresholds: {
    http_req_duration: ["p(95)<500"],
    http_req_failed: ["rate<0.15"],
  },
};

function generateNickname() {
  const num = Math.floor(Math.random() * 10000);
  return `User${num}`;
}

function extractCookie(response, cookieName) {
  const setCookie = response.headers["Set-Cookie"] || "";
  const match = setCookie.match(new RegExp(`${cookieName}=([^;]+)`));
  return match ? match[1] : null;
}

export default function () {
  const headers = { "Content-Type": "application/json" };

  const createRes = http.post(
    `${BASE_URL}/api/games`,
    JSON.stringify({ nickname: generateNickname() }),
    { headers },
  );

  const created = check(createRes, { "criar: 200": (r) => r.status === 200 });
  successRate.add(created);
  responseTime.add(createRes.timings.duration);

  if (!created || createRes.status !== 200) return;

  gamesCreated.add(1);
  const code = createRes.json("code");
  const hostToken = extractCookie(createRes, `fresh_host_${code}`);

  const joinRes = http.post(
    `${BASE_URL}/api/games/${code}/join`,
    JSON.stringify({ nickname: generateNickname() }),
    { headers },
  );

  const joined = check(joinRes, { "entrar: 200": (r) => r.status === 200 });
  successRate.add(joined);
  responseTime.add(joinRes.timings.duration);

  if (!joined || joinRes.status !== 200) return;

  playersJoined.add(1);
  const playerToken = extractCookie(joinRes, `fresh_player_${code}`);

  const startRes = http.post(`${BASE_URL}/api/games/${code}/start`, null, {
    headers: { Cookie: `fresh_host_${code}=${hostToken}` },
  });
  check(startRes, { "iniciar: 200": (r) => r.status === 200 });
  responseTime.add(startRes.timings.duration);

  sleep(0.3);

  const stateRes = http.get(`${BASE_URL}/api/games/${code}/state`, {
    headers: { Cookie: `fresh_host_${code}=${hostToken}` },
  });

  if (stateRes.status === 200) {
    const state = JSON.parse(stateRes.body);
    if (state.currentQuestion && state.currentQuestion.choices) {
      const choiceId = state.currentQuestion.choices[0].id;
      const answerRes = http.post(
        `${BASE_URL}/api/games/${code}/answer`,
        JSON.stringify({ choiceId }),
        {
          headers: {
            "Content-Type": "application/json",
            Cookie: `fresh_player_${code}=${playerToken}`,
          },
        },
      );

      const answered = check(answerRes, {
        "responder: 200": (r) => r.status === 200,
      });
      successRate.add(answered);
      responseTime.add(answerRes.timings.duration);

      if (answered) {
        answersSubmitted.add(1);
      }
    }
  }

  const finishRes = http.post(`${BASE_URL}/api/games/${code}/finish`, null, {
    headers: { Cookie: `fresh_host_${code}=${hostToken}` },
  });
  check(finishRes, { "finalizar: 200": (r) => r.status === 200 });
  responseTime.add(finishRes.timings.duration);
}

export function handleSummary(data) {
  console.log("\n========== TESTE BÁSICO - RESUMO ==========");
  console.log(
    `Total requisições: ${data.metrics.http_reqs?.values?.count || 0}`,
  );
  console.log(
    `Taxa de erro: ${
      ((data.metrics.http_req_failed?.values?.rate || 0) * 100).toFixed(2)
    }%`,
  );
  console.log(
    `Duração média: ${
      data.metrics.http_req_duration?.values?.avg?.toFixed(2) || 0
    }ms`,
  );
  console.log(
    `P95: ${
      data.metrics.http_req_duration?.values?.["p(95)"]?.toFixed(2) || 0
    }ms`,
  );
  console.log(
    `Games criados: ${data.metrics.games_created?.values?.count || 0}`,
  );
  console.log(`Jogadores: ${data.metrics.players_joined?.values?.count || 0}`);
  console.log(
    `Respostas: ${data.metrics.answers_submitted?.values?.count || 0}`,
  );
  console.log("=============================================\n");

  return {};
}
