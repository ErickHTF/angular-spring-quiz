# angular-spring-quiz

**Fresh Quiz**: jogo de perguntas em tempo real sobre fundamentos da web, no
estilo Kahoot. É a reescrita do Fresh Quiz original (Deno/Fresh + Preact) com
**Angular 22** no front e **Java 21 + Spring Boot 4** no back, mantendo as
regras do jogo, a API, as atualizações por SSE e o PostgreSQL.

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
