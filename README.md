# angular-spring-quiz

**Fresh Quiz**: a Kahoot-style real-time quiz game about web fundamentals. It's
a rewrite of the original Fresh Quiz (Deno/Fresh + Preact) with **Angular 22**
on the front end and **Java 21 + Spring Boot 4** on the back end, keeping the
game rules, the API, the SSE updates and PostgreSQL.

## Origin

Port of [freshQuiz](https://github.com/ErickHTF/freshQuiz). Game rules, API
routes (plus `GET /me`), schema and questions are the same; what changes is the
stack and a few implementation decisions:

- **Database**: in the original, `db/init/01-setup.sh` applies migrations and
  seed when the container is created (or via `deno task db:migrate`/`db:seed`);
  here **Flyway** applies `V1`–`V3` (schema and seed) when the backend starts.
- **Data access**: still hand-written SQL, swapping the `postgres` client
  (tagged templates) for Spring's `JdbcClient`, with Spring-managed
  transactions (`@Transactional`/`TransactionTemplate`).
- **Real time**: same SSE design (state computed per connection, 25 s
  heartbeat), now with `SseEmitter`, Spring application events and
  `TaskScheduler` instead of `setTimeout` for the reveal.
- **Front end**: the `useGameState` hook (global reference-counted store cache,
  Preact signals) became the `GameStateStore` service with Angular signals,
  provided by the room page and closed in `ngOnDestroy`.
- **Tests**: the original's integration tests use the local Postgres and are
  skipped if it isn't running; here they spin up Postgres with
  **Testcontainers** and also cover the HTTP layer (MockMvc). The front end has
  Vitest tests.
- The EC2 deploy (systemd + manual workflows) was not ported.

## Requirements

- Java 21 (Maven comes through the `./mvnw` wrapper)
- Node `^22.22.3`, `^24.15.0` or `>=26` (required by Angular 22) and npm
- Docker (local Postgres and Testcontainers in the tests)

## Development

In three terminals, from the repository root:

```bash
docker compose up -d                          # Postgres on port 5433
cd backend && ./mvnw spring-boot:run          # API at http://localhost:8080
cd frontend && npm install && npm start       # app at http://localhost:4200
```

Open `http://localhost:4200/` to join a game or `/host` to create a room.
`ng serve` uses `frontend/proxy.conf.json` to forward `/api` and `/health` to
Spring: the browser sees a single origin, so HttpOnly cookies and
`EventSource` work without CORS.

The schema and the initial quiz (16 questions) are applied by **Flyway** when
the backend starts (`backend/src/main/resources/db/migration`). To recreate the
database from scratch (wipes the data):
`docker compose down -v && docker compose up -d`.

The original project's compose file also publishes port `5433`; stop one
before starting the other.

Without docker-compose, `cd backend && ./mvnw spring-boot:test-run` starts the
API with a throwaway Postgres created by Testcontainers (data is lost when it
stops).

### Backend configuration

| Variable            | Default                                        |
| ------------------- | ---------------------------------------------- |
| `DATABASE_URL`      | `jdbc:postgresql://localhost:5433/fresh_quiz`  |
| `DATABASE_USER`     | `fresh`                                        |
| `DATABASE_PASSWORD` | `fresh`                                        |
| `PORT`              | `8080`                                         |

`DATABASE_URL` is a JDBC URL (`jdbc:postgresql://...`), not the `postgres://`
format used in the original project.

## Tests

- `cd backend && ./mvnw test`: scoring (unit) and integration with
  Testcontainers (game lifecycle, deadlines, timed reveal, "skip",
  authorization, cookies and error format). Requires Docker running.
- `cd frontend && npm test -- --watch=false`: Vitest (shuffling, votes and SSE
  store).
- Load: see [`loadtest/README.md`](loadtest/README.md).

## Structure

- `backend/src/main/java/com/freshquiz/game/`: domain. `GameService` (rules),
  `GameRepository` (SQL with `JdbcClient`), `RevealScheduler` (timed reveal)
  and `Scoring`.
- `backend/.../events/`: `GameEventBroker` keeps the SSE connections per room
  and sends each connection the state as seen by it (the host sees votes live).
- `backend/.../web/`: REST controllers, session cookies and error handling.
- `frontend/src/app/core/`: models, HTTP client, `GameStateStore` (SSE →
  signals), shuffling and votes.
- `frontend/src/app/pages/`: one page per route. Room pages provide the
  `GameStateStore`, shared by the game and the ranking.
- `frontend/src/app/game/` and `ui/`: game components and visual components.

## How SSE Works

1. `GET /api/games/:code/events` registers an `SseEmitter` in the
   `GameEventBroker`, together with the session's viewer (host or player), and
   immediately sends the current state.
2. Every action that changes the room (`GameController` endpoints or the
   `RevealScheduler` reveal) publishes a `GameChangedEvent` after the
   transaction commits.
3. The broker receives the event and, for each connection in that room,
   recomputes the state for that viewer and sends a `state` event — that's why
   the host sees votes live and players don't.
4. In the browser, `GameStateStore` listens for `state` and updates the
   signals; on reconnect, it fetches `/state` so no changes are missed.

Connections live in memory (one map per room code) and sending is synchronous
on the thread that published the event, so the fan-out works for a single
backend instance; running several instances would require a shared channel
(for example Postgres `LISTEN/NOTIFY`).

## Game Lifecycle

- The host creates the room at `/host` and shares the code. Players join from
  the home page with a nickname, only while the room is in the lobby.
- The host starts the quiz and watches the voting live. Each player sees the
  options in their own order and only sees the votes after answering.
- A correct answer is worth 1000 points plus up to 500 bonus, proportional to
  speed; a wrong answer is worth 0. Points are added to the scoreboard on
  reveal.
- The answer is revealed when time runs out, or earlier with "Pular pergunta"
  (Skip question), which keeps the points of those who already answered. Then
  the host moves on or ends the quiz.
- "Jogar novamente" (Play again) goes back to the lobby, resetting scores and
  answers, with the same players.
- "Sair" (Leave) clears the session cookies; the player stays listed in the
  ranking.
- Reveal timers live in memory: restarting the backend loses them, and they are
  rescheduled on the next read of the room state.

## API

| Method | Route                      | Description                                       |
| ------ | -------------------------- | ------------------------------------------------- |
| POST   | `/api/games`               | Creates a room `{nickname}` (sets host cookie).   |
| POST   | `/api/games/:code/join`    | Joins the room `{nickname}`; returns `playerId`.  |
| GET    | `/api/games/:code/me`      | Role, `playerId` and nickname of the session.     |
| GET    | `/api/games/:code/state`   | Current game state.                               |
| GET    | `/api/games/:code/events`  | Real-time SSE stream (`state` event).             |
| POST   | `/api/games/:code/start`   | Starts the first question (host).                 |
| POST   | `/api/games/:code/answer`  | Records the answer `{choiceId}` (player).         |
| POST   | `/api/games/:code/skip`    | Reveals the question before the deadline (host).  |
| POST   | `/api/games/:code/next`    | Moves on after the reveal (host).                 |
| POST   | `/api/games/:code/finish`  | Ends the quiz early (host).                       |
| POST   | `/api/games/:code/restart` | Back to the lobby, resetting the scores (host).   |
| POST   | `/api/games/:code/leave`   | Clears the session cookies.                       |
| GET    | `/health`                  | Health check (app + database); 503 without DB.    |

Room codes are case-insensitive. Errors come as `{"error": "message"}`: `400`
for business rules (e.g. "O tempo acabou." — time's up), `401` with no session
in the room (`me`, `state`, `events`; includes a nonexistent room), `403` when
the action belongs to another role and `422` on `answer` without `choiceId`.
