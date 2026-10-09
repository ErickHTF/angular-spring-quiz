# Testes de carga

Scripts [k6](https://k6.io/) e um teste rápido em shell para a API. O alvo
padrão é o backend local (`http://localhost:8080`); suba o Postgres e o Spring
antes (veja o README da raiz).

## Pré-requisitos

```bash
brew install k6
```

## Como executar

A partir da raiz do repositório:

```bash
k6 run loadtest/basic.js       # rampa até 100 usuários virtuais, ~1min30
k6 run loadtest/realistic.js   # mesmo fluxo, patamares mais longos, ~1min50
k6 run loadtest/stress.js      # rampa até 500 usuários virtuais, ~5min
./loadtest/quick-test.sh       # só curl: health, criar, entrar e concorrência
```

Para apontar para outro servidor:

```bash
k6 run --env BASE_URL=http://outro-host:8080 loadtest/basic.js
./loadtest/quick-test.sh http://outro-host:8080 50 10   # url, requisições, workers
```

## Fluxo testado (k6)

Cada iteração de usuário virtual cria uma sala com um único jogador:

1. Criar sala (`POST /api/games`)
2. Entrar na sala (`POST /api/games/{code}/join`)
3. Iniciar a partida (`POST /api/games/{code}/start`)
4. Consultar o estado (`GET /api/games/{code}/state`)
5. Responder a primeira pergunta (`POST /api/games/{code}/answer`)
6. Encerrar a partida (`POST /api/games/{code}/finish`)

Os três scripts usam o mesmo fluxo e só mudam a rampa de usuários e os limites
(`p95 < 500ms` no básico e no realista, `p95 < 1s` no stress; erro `< 15%`). Eles
não abrem conexões SSE, então não medem o custo dos streams em tempo real.

Cada execução cria salas e jogadores no banco. Para limpar:
`docker compose down -v && docker compose up -d`.
