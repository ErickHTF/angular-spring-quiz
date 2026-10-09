# Fresh Quiz (Angular + Spring Boot)

Jogo de perguntas em tempo real sobre fundamentos da web. Reescrita do
Fresh Quiz original (Deno/Fresh + Preact) com **Angular 22**
no front e **Java 21 + Spring Boot 4** no back, mantendo SSE e PostgreSQL.

## Origem

Port do [freshQuiz](https://github.com/ErickHTF/freshQuiz). Regras do jogo,
rotas da API (mais `GET /me`), schema e perguntas são os mesmos; o que muda é a
stack e algumas decisões de implementação:

- **Banco**: no original, `db/init/01-setup.sh` aplica migrações e seed na
  criação do container (ou via `deno task db:migrate`/`db:seed`); aqui o
  **Flyway** aplica `V1`–`V3` (schema e seed) na subida do backend.
- **Acesso a dados**: continua SQL escrito à mão, trocando o cliente `postgres`
  (tagged templates) pelo `JdbcClient` do Spring, com transações gerenciadas
  pelo Spring (`@Transactional`/`TransactionTemplate`).
- **Tempo real**: mesmo desenho de SSE (estado calculado por conexão, heartbeat
  de 25 s), agora com `SseEmitter`, eventos de aplicação do Spring e o
  `TaskScheduler` no lugar do `setTimeout` para a revelação.
- **Front**: o hook `useGameState` (cache global de stores com contagem de
  referências, Preact signals) virou o serviço `GameStateStore` com signals do
  Angular, fornecido pela página da sala e fechado no `ngOnDestroy`.
- **Testes**: os testes de integração do original usam o Postgres local e são
  pulados se ele não estiver no ar; aqui sobem um Postgres com
  **Testcontainers** e cobrem também a camada HTTP (MockMvc). O front tem testes
  Vitest.
- O deploy na EC2 (systemd + workflows manuais) não foi portado.

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

## Como funciona o SSE

1. `GET /api/games/:code/events` registra um `SseEmitter` no `GameEventBroker`,
   junto com o viewer (host ou jogador) da sessão, e já envia o estado atual.
2. Toda ação que muda a sala (endpoints do `GameController` ou a revelação do
   `RevealScheduler`) publica um `GameChangedEvent` depois de concluir a
   transação.
3. O broker recebe o evento e, para cada conexão daquela sala, recalcula o
   estado para aquele viewer e envia um evento `state` — por isso o host vê os
   votos ao vivo e os jogadores não.
4. No navegador, o `GameStateStore` escuta `state` e atualiza os signals; ao
   reconectar, busca `/state` para não perder mudanças.

As conexões ficam em memória (um mapa por código de sala) e o envio é síncrono
na thread que publicou o evento, então o fan-out vale para uma única instância
do backend; rodar várias instâncias exigiria um canal compartilhado (por exemplo
`LISTEN/NOTIFY` do Postgres).

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
