# Load Tests

[k6](https://k6.io/) scripts and a quick shell test for the API. The default
target is the local backend (`http://localhost:8080`); start Postgres and Spring
first (see the root README).

## Prerequisites

```bash
brew install k6
```

## Running

From the repository root:

```bash
k6 run loadtest/basic.js       # ramps up to 100 virtual users, ~1min30
k6 run loadtest/realistic.js   # same flow, longer plateaus, ~1min50
k6 run loadtest/stress.js      # ramps up to 500 virtual users, ~5min
./loadtest/quick-test.sh       # curl only: health, create, join and concurrency
```

To target another server:

```bash
k6 run --env BASE_URL=http://other-host:8080 loadtest/basic.js
./loadtest/quick-test.sh http://other-host:8080 50 10   # url, requests, workers
```

## Tested Flow (k6)

Each virtual user iteration creates a room with a single player:

1. Create a room (`POST /api/games`)
2. Join the room (`POST /api/games/{code}/join`)
3. Start the game (`POST /api/games/{code}/start`)
4. Fetch the state (`GET /api/games/{code}/state`)
5. Answer the first question (`POST /api/games/{code}/answer`)
6. End the game (`POST /api/games/{code}/finish`)

All three scripts use the same flow and only change the user ramp and the
thresholds (`p95 < 500ms` for basic and realistic, `p95 < 1s` for stress; errors
`< 15%`). They don't open SSE connections, so they don't measure the cost of the
real-time streams.

Each run creates rooms and players in the database. To clean up:
`docker compose down -v && docker compose up -d`.
