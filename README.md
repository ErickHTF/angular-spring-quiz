# Fresh Quiz (Angular + Spring Boot)

Jogo de perguntas em tempo real sobre fundamentos da web. Reescrita do
Fresh Quiz original (Deno/Fresh + Preact) com **Angular 22**
no front e **Java 21 + Spring Boot 4** no back, mantendo SSE e PostgreSQL.

## Requisitos

- Java 21 (o Maven vem pelo wrapper `./mvnw`)
- Node 22+ e npm
- Docker (Postgres local e Testcontainers nos testes)

## Desenvolvimento

```bash
docker compose up -d                 # Postgres na porta 5433
cd backend && ./mvnw spring-boot:run # API em http://localhost:8080
cd frontend && npm install && npm start  # http://localhost:4200
```

Abra `http://localhost:4200/` para entrar em uma partida ou `/host` para criar
uma sala. O `ng serve` usa `frontend/proxy.conf.json` para encaminhar `/api` e
`/health` ao Spring: o navegador enxerga uma única origem, então os cookies
HttpOnly e o `EventSource` funcionam sem CORS.

O schema e o quiz inicial são aplicados pelo **Flyway** na subida do backend
(`backend/src/main/resources/db/migration`). Para recriar o banco do zero (apaga
os dados): `docker compose down -v && docker compose up -d`.

## Testes

- `cd backend && ./mvnw test` — unidade (pontuação) e integração com
  Testcontainers (ciclo da partida, prazos, autorização, cookies).
- `cd frontend && npm test -- --watch=false` — Vitest (embaralhamento, votos,
  store SSE).
- Carga: `k6 run loadtest/basic.js` (alvo padrão `http://localhost:8080`).

## Estrutura

- `backend/src/main/java/com/freshquiz/game/`: domínio — `GameService`
  (regras), `GameRepository` (SQL com `JdbcClient`), `RevealScheduler`
  (revelação por timer), `Scoring`.
- `backend/.../events/`: `GameEventBroker` mantém as conexões SSE por sala e
  envia o estado calculado para cada viewer (host vê votos ao vivo).
- `backend/.../web/`: controllers REST, cookies de sessão e tratamento de erros.
- `frontend/src/app/core/`: modelos, cliente HTTP, `GameStateStore` (SSE →
  signals), embaralhamento e votos.
- `frontend/src/app/pages/`: páginas por rota; as páginas de sala fornecem o
  `GameStateStore`, compartilhado por jogo e ranking.
- `frontend/src/app/game/` e `ui/`: componentes do jogo e visuais.

## Ciclo da partida

- Host cria a sala em `/host` e compartilha o código; jogadores entram pela
  página inicial com um apelido.
- O host inicia o quiz e acompanha a votação ao vivo; a resposta é revelada
  quando o tempo acaba (ou ao "Pular pergunta", que mantém os pontos de quem já
  respondeu). Depois o host avança ou encerra.
- "Jogar novamente" volta ao lobby zerando placar e respostas.
- Os timers de revelação ficam em memória: reiniciar o backend os perde, e eles
  são reagendados na próxima leitura do estado da sala.

## API

| Método | Rota                       | Descrição                                     |
| ------ | -------------------------- | --------------------------------------------- |
| POST   | `/api/games`               | Cria sala (define cookie de host).            |
| POST   | `/api/games/:code/join`    | Entra na sala (cookie do jogador + playerId). |
| GET    | `/api/games/:code/me`      | Papel, playerId e apelido da sessão atual.    |
| GET    | `/api/games/:code/state`   | Estado atual da partida.                      |
| GET    | `/api/games/:code/events`  | Stream SSE com o estado em tempo real.        |
| POST   | `/api/games/:code/start`   | Inicia a primeira pergunta (host).            |
| POST   | `/api/games/:code/answer`  | Registra a resposta do jogador.               |
| POST   | `/api/games/:code/skip`    | Revela a pergunta antes do prazo (host).      |
| POST   | `/api/games/:code/next`    | Avança a pergunta revelada (host).            |
| POST   | `/api/games/:code/finish`  | Encerra o quiz antecipadamente (host).        |
| POST   | `/api/games/:code/restart` | Volta ao lobby zerando placar (host).         |
| POST   | `/api/games/:code/leave`   | Limpa os cookies da sessão.                   |
| GET    | `/health`                  | Health check (app + banco).                   |
