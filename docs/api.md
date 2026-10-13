# API reference

[← Back to README](../README.md)

| Method | Route                      | Description                                      |
| ------ | -------------------------- | ------------------------------------------------ |
| POST   | `/api/games`               | Creates a room `{nickname}` (sets host cookie).  |
| POST   | `/api/games/:code/join`    | Joins the room `{nickname}`; returns `playerId`. |
| GET    | `/api/games/:code/me`      | Role, `playerId` and nickname of the session.    |
| GET    | `/api/games/:code/state`   | Current game state.                              |
| GET    | `/api/games/:code/events`  | Real-time SSE stream (`state` event).            |
| POST   | `/api/games/:code/start`   | Starts the first question (host).                |
| POST   | `/api/games/:code/answer`  | Records the answer `{choiceId}` (player).        |
| POST   | `/api/games/:code/skip`    | Reveals the question before the deadline (host). |
| POST   | `/api/games/:code/next`    | Moves on after the reveal (host).                |
| POST   | `/api/games/:code/finish`  | Ends the quiz early (host).                      |
| POST   | `/api/games/:code/restart` | Back to the lobby, resetting the scores (host).  |
| POST   | `/api/games/:code/leave`   | Clears the session cookies.                      |
| GET    | `/health`                  | Health check (app + database); 503 without DB.   |

Room codes are case-insensitive. Errors come as `{"error": "message"}`: `400`
for business rules (e.g. "O tempo acabou." — time's up), `401` with no session
in the room (`me`, `state`, `events`; includes a nonexistent room), `403` when
the action belongs to another role and `422` on `answer` without `choiceId`.
