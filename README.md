# angular-spring-quiz

**Fresh Quiz**: jogo de perguntas em tempo real sobre fundamentos da web, no
estilo Kahoot. É a reescrita do Fresh Quiz original (Deno/Fresh + Preact) com
**Angular 22** no front e **Java 21 + Spring Boot 4** no back, mantendo as
regras do jogo, a API, as atualizações por SSE e o PostgreSQL.

## Requisitos

- Java 21 (o Maven vem pelo wrapper `./mvnw`)
- Node `^22.22.3`, `^24.15.0` ou `>=26` (exigência do Angular 22) e npm
- Docker (Postgres local e Testcontainers nos testes)

## Desenvolvimento

Em três terminais, a partir da raiz do repositório:

```bash
docker compose up -d                          # Postgres na porta 5433
cd backend && ./mvnw spring-boot:run          # API em http://localhost:8080
cd frontend && npm install && npm start       # app em http://localhost:4200
```

Abra `http://localhost:4200/` para entrar em uma partida ou `/host` para criar
uma sala. O `ng serve` usa `frontend/proxy.conf.json` para encaminhar `/api` e
`/health` ao Spring: o navegador enxerga uma única origem, então os cookies
HttpOnly e o `EventSource` funcionam sem CORS.

O schema e o quiz inicial (16 perguntas) são aplicados pelo **Flyway** quando o
backend sobe (`backend/src/main/resources/db/migration`). Para recriar o banco
do zero (apaga os dados): `docker compose down -v && docker compose up -d`.

O compose do projeto original também publica a porta `5433`; derrube um antes
de subir o outro.

Sem docker-compose, `cd backend && ./mvnw spring-boot:test-run` sobe a API com
um Postgres descartável criado pelo Testcontainers (os dados somem ao parar).

### Configuração do backend

| Variável            | Padrão                                         |
| ------------------- | ---------------------------------------------- |
| `DATABASE_URL`      | `jdbc:postgresql://localhost:5433/fresh_quiz`  |
| `DATABASE_USER`     | `fresh`                                        |
| `DATABASE_PASSWORD` | `fresh`                                        |
| `PORT`              | `8080`                                         |

`DATABASE_URL` é uma URL JDBC (`jdbc:postgresql://...`), não o formato
`postgres://` usado no projeto original.

## Testes

- `cd backend && ./mvnw test`: pontuação (unidade) e integração com
  Testcontainers (ciclo da partida, prazos, revelação por timer, "pular",
  autorização, cookies e formato dos erros). Precisa do Docker rodando.
- `cd frontend && npm test -- --watch=false`: Vitest (embaralhamento, votos e
  store SSE).
- Carga: veja [`loadtest/README.md`](loadtest/README.md).

## Estrutura

- `backend/src/main/java/com/freshquiz/game/`: domínio. `GameService` (regras),
  `GameRepository` (SQL com `JdbcClient`), `RevealScheduler` (revelação por
  timer) e `Scoring`.
- `backend/.../events/`: `GameEventBroker` mantém as conexões SSE por sala e
  envia a cada conexão o estado visto por ela (o host vê os votos ao vivo).
- `backend/.../web/`: controllers REST, cookies de sessão e tratamento de erros.
- `frontend/src/app/core/`: modelos, cliente HTTP, `GameStateStore` (SSE →
  signals), embaralhamento e votos.
- `frontend/src/app/pages/`: uma página por rota. As páginas de sala fornecem o
  `GameStateStore`, compartilhado pelo jogo e pelo ranking.
- `frontend/src/app/game/` e `ui/`: componentes do jogo e componentes visuais.

## Ciclo da partida

- O host cria a sala em `/host` e compartilha o código. Jogadores entram pela
  página inicial com um apelido, só enquanto a sala está no lobby.
- O host inicia o quiz e acompanha a votação ao vivo. Cada jogador vê as
  alternativas em uma ordem própria e só vê os votos depois de responder.
- Acerto vale 1000 pontos mais até 500 de bônus, proporcional à rapidez; erro
  vale 0. Os pontos entram no placar na revelação.
- A resposta é revelada quando o tempo acaba, ou antes, com "Pular pergunta"
  (que mantém os pontos de quem já respondeu). Depois o host avança ou encerra.
- "Jogar novamente" volta ao lobby zerando placar e respostas, com os mesmos
  jogadores.
- "Sair" apaga os cookies da sessão; o jogador continua listado no ranking.
- Os timers de revelação ficam em memória: reiniciar o backend os perde, e eles
  são reagendados na próxima leitura do estado da sala.

## API

| Método | Rota                       | Descrição                                       |
| ------ | -------------------------- | ----------------------------------------------- |
| POST   | `/api/games`               | Cria sala `{nickname}` (define cookie de host). |
| POST   | `/api/games/:code/join`    | Entra na sala `{nickname}`; devolve `playerId`. |
| GET    | `/api/games/:code/me`      | Papel, `playerId` e apelido da sessão atual.    |
| GET    | `/api/games/:code/state`   | Estado atual da partida.                        |
| GET    | `/api/games/:code/events`  | Stream SSE (evento `state`) em tempo real.      |
| POST   | `/api/games/:code/start`   | Inicia a primeira pergunta (host).              |
| POST   | `/api/games/:code/answer`  | Registra a resposta `{choiceId}` (jogador).     |
| POST   | `/api/games/:code/skip`    | Revela a pergunta antes do prazo (host).        |
| POST   | `/api/games/:code/next`    | Avança após a revelação (host).                 |
| POST   | `/api/games/:code/finish`  | Encerra o quiz antecipadamente (host).          |
| POST   | `/api/games/:code/restart` | Volta ao lobby zerando o placar (host).         |
| POST   | `/api/games/:code/leave`   | Apaga os cookies da sessão.                     |
| GET    | `/health`                  | Health check (app + banco); 503 sem banco.      |

O código da sala não diferencia maiúsculas de minúsculas. Erros vêm como
`{"error": "mensagem"}`: `400` para regra de negócio (ex.: "O tempo acabou."),
`401` sem sessão na sala (`me`, `state`, `events`; inclui sala inexistente),
`403` quando a ação é de outro papel e `422` em `answer` sem `choiceId`.
