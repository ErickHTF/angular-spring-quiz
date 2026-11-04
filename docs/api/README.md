# API REST e SSE

[← Voltar ao README](../../README.md)

Base: `http://localhost:8080` (no desenvolvimento o Angular encaminha `/api` e
`/health` pelo `frontend/proxy.conf.json`). Corpos em JSON.

## Quizzes (Manter Quizzes)

| Método | Rota               | Corpo                   | Resposta                                  |
| ------ | ------------------ | ----------------------- | ----------------------------------------- |
| GET    | `/api/quizzes`     | —                       | `[{id, title, questionCount, canEdit}]`   |
| POST   | `/api/quizzes`     | `{title, questions}`    | `{id, title, questionCount, canEdit}`     |
| GET    | `/api/quizzes/:id` | —                       | `{id, title, questions, canEdit}`         |
| PUT    | `/api/quizzes/:id` | `{title, questions}`    | `{id, title, questionCount, canEdit}`     |
| DELETE | `/api/quizzes/:id` | —                       | `{ok: true}`                              |

Formato de `questions`:

```json
[
  {
    "prompt": "Qual tag cria um link?",
    "durationSeconds": 20,
    "choices": [
      { "label": "<a>", "isCorrect": true },
      { "label": "<link>", "isCorrect": false }
    ]
  }
]
```

Regras validadas no backend (`QuizService`) e no front (`quiz-draft.ts`):

- título com 3 a 80 caracteres; de 1 a 30 perguntas;
- enunciado de cada pergunta com 3 a 300 caracteres; tempo de 5 a 120 s;
- de 2 a 4 alternativas, com 1 a 120 caracteres cada, sem repetição;
- exatamente uma alternativa correta.

**Dono do quiz.** O `POST` gera um token aleatório. O banco guarda apenas o hash
SHA-256 dele (`quizzes.owner_token_hash`). O token volta para o navegador no
cookie HttpOnly `fresh_quiz_<id>`, com validade de 30 dias, e também no
cabeçalho `X-Quiz-Token`. `PUT` e `DELETE` exigem esse token, enviado pelo
cookie, pelo `X-Quiz-Token` ou por `Authorization: Bearer`. Sem ele, a API
responde `400` com "Você não tem permissão…". Também não é possível excluir um
quiz que já tenha partidas registradas. O quiz padrão (seed do Flyway) não
tem dono, então ninguém pode editá-lo.

## Partidas

| Método | Rota                       | Descrição                                       |
| ------ | -------------------------- | ----------------------------------------------- |
| POST   | `/api/games`               | Cria sala `{nickname, quizId?}` (cookie host).  |
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

Sem `quizId`, a sala usa o quiz padrão. As sessões ficam nos cookies HttpOnly
`fresh_host_<code>` e `fresh_player_<code>`, com validade de 1 dia.

## Stream SSE

`GET /api/games/:code/events` responde `text/event-stream`. A cada mudança na
sala, publicada depois do commit da transação, o servidor envia:

```
event: state
data: {"code":"ABC123","status":"question","currentQuestion":{...},"players":[...],...}
```

O estado é calculado para quem está conectado. O host recebe
`currentQuestion.answerCounts` (votos ao vivo). O jogador só vê
`correctChoiceId` depois da revelação. A cada 25 s vai um comentário de
heartbeat para manter a conexão aberta.

## Erros

O formato é sempre `{"error": "mensagem"}`.

| Status | Quando |
| ------ | ------ |
| `400`  | Regra de negócio (ex.: "O tempo acabou.", validação do quiz, sem permissão no quiz). |
| `401`  | Sem sessão na sala (`me`, `state`, `events`; inclui sala inexistente). |
| `403`  | A ação pertence a outro papel (ex.: jogador chamando `start`). |
| `422`  | `answer` sem `choiceId`. |
