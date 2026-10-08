# Load Testing - fresh-quiz-ng

Testes de carga para o app de quiz/game.

## Pré-requisitos

```bash
brew install k6
```

## Como executar

```bash
cd ~/Projects/fresh-quiz-ng

# Teste básico (50-100 usuários)
k6 run loadtest/basic.js

# Teste de estresse (até 500 usuários)
k6 run loadtest/stress.js

# Teste realista (cenário completo)
k6 run loadtest/realistic.js

# Teste rápido (curl)
./loadtest/quick-test.sh
```

## URLs

```bash
# Produção (padrão)
k6 run loadtest/basic.js

# Local
k6 run --env BASE_URL=http://localhost:8080 loadtest/basic.js
```

## Fluxo testado

1. Criar jogo (`POST /api/games`)
2. Entrar no jogo (`POST /api/games/{code}/join`)
3. Iniciar jogo (`POST /api/games/{code}/start`)
4. Consultar estado (`GET /api/games/{code}/state`)
5. Responder pergunta (`POST /api/games/{code}/answer`)
6. Finalizar jogo (`POST /api/games/{code}/finish`)
