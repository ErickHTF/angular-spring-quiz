# Modelagem

[← Voltar ao README](../../README.md)

| Arquivo | Conteúdo |
| ------- | -------- |
| [`fresh.asta`](fresh.asta) | Modelo Astah com todos os diagramas (casos de uso, classes e pacotes). |
| [`der-angular-spring-quiz.png`](der-angular-spring-quiz.png) | DER do banco PostgreSQL (tabelas criadas pelas migrations Flyway). |
| [`casos-de-uso.puml`](casos-de-uso.puml) | Diagrama de casos de uso em PlantUML. |
| [`classes-dominio.puml`](classes-dominio.puml) | Diagrama de classes de domínio em PlantUML. |
| [`diagrama-pacotes.puml`](diagrama-pacotes.puml) | Diagrama de pacotes da arquitetura (backend e frontend). |

Os `.puml` podem ser renderizados com a extensão PlantUML do VS Code ou em
<https://www.plantuml.com/plantuml>.

## Casos de uso

| Ator | Caso de uso | Onde está no sistema |
| ---- | ----------- | -------------------- |
| Jogador | Entrar na Sala | Home (`/`) → `POST /api/games/:code/join` |
| Jogador | Responder Pergunta | `/play/:code` → `POST /api/games/:code/answer` |
| Jogador | Sair da Sala | `/play/:code` → `POST /api/games/:code/leave` |
| Anfitrião | Gerenciar partida | `/host` e `/host/:code` → `start`, `skip`, `next`, `finish`, `restart` |
| Anfitrião | Manter Quizzes | `/quizzes`, `/quizzes/new`, `/quizzes/:id/edit` → `/api/quizzes` |
| Temporizador | Revelar Resposta por Tempo Esgotado | `RevealScheduler` (backend) |

## Classes de domínio × banco

As classes do diagrama correspondem às tabelas do DER: `Game` → `games`,
`Quiz` → `quizzes`, `Question` → `questions`, `Choice` → `choices`,
`Player` → `players`, `GamePlayer` → `game_players` e `Answer` → `answers`.
No código, as regras ficam nos serviços (`GameService`, `QuizService`) e os
dados trafegam como `record`s Java em `game/model` e `quiz/model`.
