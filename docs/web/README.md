# Documentacao Front-end Web - Fresh Quiz

A interface do **Fresh Quiz** e construida com **Angular 22** utilizando Standalone Components, Signals reativos, Tailwind CSS e comunicacao em tempo real via Server-Sent Events (SSE).

---

## 1. Arquitetura da Aplicacao

```
frontend/src/app/
├── app.config.ts           # Provedores da aplicacao (roteamento, HttpClient com credentials)
├── app.routes.ts           # Definicao das rotas
├── app.ts                  # Componente raiz com router-outlet
├── core/
│   ├── game-api.ts         # Servico HTTP para endpoints REST
│   ├── game-state.store.ts # Signal Store reativo conectado ao SSE da sala
│   ├── models.ts           # Modelos de dados e tipos TypeScript
│   ├── shuffle.ts          # Algoritmo de embaralhamento das alternativas para cada jogador
│   └── votes.ts            # Calculo de distribuicao de votos na revelacao
├── pages/
│   ├── home-page.ts        # Tela inicial (entrar na sala com codigo e apelido)
│   ├── host-landing-page.ts# Entrada do host (definir apelido e criar sala)
│   ├── host-game-page.ts   # Painel de controle do host na partida
│   └── player-game-page.ts # Experiencia interativa do jogador na partida
├── game/
│   ├── host-game.ts        # Visao do host (lobby, pergunta com contagem de votos, ranking)
│   ├── player-game.ts      # Visao do jogador (botoes de alternativas, feedback de acerto/erro)
│   └── ranking.ts          # Tabela de classificacao e pontuacoes
└── ui/
    ├── brand.ts            # Identidade visual e logotipo
    ├── countdown.ts        # Barra e relogio regressivo sincronizado com o prazo do backend
    └── podium.ts           # Podium dos 3 melhores colocados na finalizacao
```

---

## 2. Mapa de Rotas

| Rota | Pagina / Componente | Papel | Descricao |
| :--- | :--- | :--- | :--- |
| `/` | `HomePage` | Publico / Player | Formulario para digitar o codigo de 6 caracteres e o apelido. |
| `/host` | `HostLandingPage` | Publico / Host | Tela para o anfitriao definir seu apelido e iniciar uma sala. |
| `/host/:code` | `HostGamePage` | Host | Painel completo do host com controles de avancar, pular, iniciar e reiniciar. |
| `/play/:code` | `PlayerGamePage` | Player | Tela de jogo do participante (lobby de espera, envio de respostas e pontuacao). |

---

## 3. Integracao Reativa e Tempo Real (SSE + Signals)

O gerenciamento de estado e centralizado em [`GameStateStore`](file:///C:/Users/mario/OneDrive/%C3%81rea%20de%20Trabalho/agente-gemini/angular-spring-quiz/frontend/src/app/core/game-state.store.ts):
1. Ao ingressar em `/host/:code` ou `/play/:code`, a store abre uma conexao `EventSource('/api/games/' + code + '/events')`.
2. O servidor Spring Boot envia eventos `state` a cada alteracao na partida (novos jogadores, mudancas de fase, submissao de respostas, conclusao).
3. A store atualiza signals nativos do Angular (`status`, `players`, `currentQuestion`, `role`, `viewerHasAnswered`), disparando re-renderizacoes ultra-leves sem necessidade de polling.

---

## 4. Evolucao Futura: Interface para "Gerenciar Quizzes e Perguntas"

Com a adicao do caso de uso de criacao e edicao de perguntas:
- A tela de criacao de sala do Host (`HostLandingPage`) passara a oferecer a selecao de quizzes disponiveis.
- Uma nova secao/rota (ex.: `/quizzes` e `/quizzes/new`) permitira ao anfitriao cadastrar titulos, adicionar novas perguntas de multipla escolha, editar o tempo por questao (5 a 120s) e excluir perguntas indesejadas antes de publicar a sala.
