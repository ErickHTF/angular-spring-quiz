# Architecture and design

[← Back to README](../README.md)

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
