# Development guide

[← Back to README](../README.md)

## Requirements

- Java 21 (Maven comes through the `./mvnw` wrapper)
- Node `^22.22.3`, `^24.15.0` or `>=26` (required by Angular 22) and npm
- Docker (local Postgres and Testcontainers in the tests)

## Development

In three terminals, from the repository root:

```bash
docker compose up -d                          # Postgres on port 5433
cd backend && ./mvnw spring-boot:run          # API at http://localhost:8080
cd frontend && npm ci && npm start       # app at http://localhost:4200
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

| Variable            | Default                                       |
| ------------------- | --------------------------------------------- |
| `DATABASE_URL`      | `jdbc:postgresql://localhost:5433/fresh_quiz` |
| `DATABASE_USER`     | `fresh`                                       |
| `DATABASE_PASSWORD` | `fresh`                                       |
| `PORT`              | `8080`                                        |

`DATABASE_URL` is a JDBC URL (`jdbc:postgresql://...`), not the `postgres://`
format used in the original project.

## Tests

- `cd backend && ./mvnw test`: scoring (unit) and integration with
  Testcontainers (game lifecycle, deadlines, timed reveal, "skip",
  authorization, cookies and error format). Requires Docker running.
- `cd frontend && npm test -- --watch=false`: Vitest (shuffling, votes and SSE
  store).
- Load: see [`loadtest/README.md`](../loadtest/README.md).
