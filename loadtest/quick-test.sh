#!/bin/bash

# Teste de carga rápido usando só curl (sem k6).
# Uso: ./quick-test.sh [base_url] [num_requests] [workers]
#   base_url      padrão http://localhost:8080
#   num_requests  requisições de health e de join (padrão 50); cria num_requests/5 salas
#   workers       workers paralelos no teste de concorrência, 10 criações cada (padrão 10)

BASE_URL="${1:-http://localhost:8080}"
NUM_REQUESTS="${2:-50}"
CONCURRENT="${3:-10}"
NUM_GAMES=$((NUM_REQUESTS / 5))
[ "$NUM_GAMES" -lt 1 ] && NUM_GAMES=1

# Relógio com milissegundos que funciona no macOS e no Linux (`date +%N` não existe no macOS).
now() {
    perl -MTime::HiRes=time -e 'printf "%.3f\n", time'
}

# Média em segundos; imprime 0 quando não houve requisições.
average() {
    if [ "$2" -gt 0 ]; then echo "scale=4; $1 / $2" | bc; else echo 0; fi
}

echo "======================================"
echo "  Load Test Rápido - angular-spring-quiz"
echo "======================================"
echo "URL: $BASE_URL"
echo "Requisições: $NUM_REQUESTS"
echo "Workers concorrentes: $CONCURRENT"
echo "======================================"

# Cria uma sala; imprime "status,tempo,codigo".
test_create_game() {
    local body
    body=$(curl -s -w "\n%{http_code},%{time_total}" \
        -X POST "$BASE_URL/api/games" \
        -H "Content-Type: application/json" \
        -d "{\"nickname\":\"LoadTest$RANDOM\"}")
    local code
    code=$(echo "$body" | head -1 | sed -n 's/.*"code":"\([A-Z0-9]*\)".*/\1/p')
    echo "$(echo "$body" | tail -1),$code"
}

# Entra em uma sala existente; imprime "status,tempo".
test_join_game() {
    curl -s -o /dev/null -w "%{http_code},%{time_total}" \
        -X POST "$BASE_URL/api/games/$1/join" \
        -H "Content-Type: application/json" \
        -d "{\"nickname\":\"Player$RANDOM\"}"
}

test_health() {
    curl -s -o /dev/null -w "%{http_code},%{time_total}" "$BASE_URL/health"
}

echo ""
echo "[1/4] Testando health endpoint..."
echo "--------------------------------------"

HEALTH_SUCCESSES=0
HEALTH_FAILURES=0
HEALTH_TOTAL_TIME=0

for i in $(seq 1 "$NUM_REQUESTS"); do
    result=$(test_health)
    status=$(echo "$result" | cut -d',' -f1)
    time=$(echo "$result" | cut -d',' -f2)

    if [ "$status" = "200" ]; then
        HEALTH_SUCCESSES=$((HEALTH_SUCCESSES + 1))
    else
        HEALTH_FAILURES=$((HEALTH_FAILURES + 1))
    fi

    HEALTH_TOTAL_TIME=$(echo "$HEALTH_TOTAL_TIME + $time" | bc)

    if [ $((i % 10)) -eq 0 ]; then
        echo "  $i/$NUM_REQUESTS completados..."
    fi
done

HEALTH_AVG=$(average "$HEALTH_TOTAL_TIME" "$NUM_REQUESTS")
echo ""
echo "  Resultados Health:"
echo "  Sucessos: $HEALTH_SUCCESSES/$NUM_REQUESTS"
echo "  Falhas: $HEALTH_FAILURES/$NUM_REQUESTS"
echo "  Tempo médio: ${HEALTH_AVG}s"

echo ""
echo "[2/4] Testando criar salas ($NUM_GAMES)..."
echo "--------------------------------------"

CREATE_SUCCESSES=0
CREATE_FAILURES=0
CREATE_TOTAL_TIME=0
GAME_CODES=()

for i in $(seq 1 "$NUM_GAMES"); do
    result=$(test_create_game)
    status=$(echo "$result" | cut -d',' -f1)
    time=$(echo "$result" | cut -d',' -f2)
    code=$(echo "$result" | cut -d',' -f3)

    if [ "$status" = "200" ] && [ -n "$code" ]; then
        CREATE_SUCCESSES=$((CREATE_SUCCESSES + 1))
        GAME_CODES+=("$code")
    else
        CREATE_FAILURES=$((CREATE_FAILURES + 1))
    fi

    CREATE_TOTAL_TIME=$(echo "$CREATE_TOTAL_TIME + $time" | bc)
done

CREATE_AVG=$(average "$CREATE_TOTAL_TIME" "$NUM_GAMES")
echo "  Sucessos: $CREATE_SUCCESSES/$NUM_GAMES"
echo "  Falhas: $CREATE_FAILURES/$NUM_GAMES"
echo "  Tempo médio: ${CREATE_AVG}s"

echo ""
echo "[3/4] Testando entrar nas salas criadas..."
echo "--------------------------------------"

JOIN_SUCCESSES=0
JOIN_FAILURES=0
JOIN_TOTAL_TIME=0

if [ "${#GAME_CODES[@]}" -eq 0 ]; then
    echo "  Nenhuma sala criada; pulando."
else
    for i in $(seq 1 "$NUM_REQUESTS"); do
        # Distribui os jogadores entre as salas criadas no passo anterior.
        code=${GAME_CODES[$(( (i - 1) % ${#GAME_CODES[@]} ))]}
        result=$(test_join_game "$code")
        status=$(echo "$result" | cut -d',' -f1)
        time=$(echo "$result" | cut -d',' -f2)

        if [ "$status" = "200" ]; then
            JOIN_SUCCESSES=$((JOIN_SUCCESSES + 1))
        else
            JOIN_FAILURES=$((JOIN_FAILURES + 1))
        fi

        JOIN_TOTAL_TIME=$(echo "$JOIN_TOTAL_TIME + $time" | bc)
    done
fi

JOIN_AVG=$(average "$JOIN_TOTAL_TIME" "$NUM_REQUESTS")
echo "  Sucessos: $JOIN_SUCCESSES/$NUM_REQUESTS"
echo "  Falhas: $JOIN_FAILURES/$NUM_REQUESTS"
echo "  Tempo médio: ${JOIN_AVG}s"

echo ""
echo "[4/4] Teste de concorrência..."
echo "--------------------------------------"

CONC_REQUESTS=$((CONCURRENT * 10))
CONC_START=$(now)

for i in $(seq 1 "$CONCURRENT"); do
    (
        for j in $(seq 1 10); do
            curl -s -o /dev/null \
                -X POST "$BASE_URL/api/games" \
                -H "Content-Type: application/json" \
                -d "{\"nickname\":\"Concurrent$i-$j\"}" &
        done
        wait
    ) &
done

wait

CONC_END=$(now)
CONC_DURATION=$(echo "$CONC_END - $CONC_START" | bc)

echo "  $CONC_REQUESTS criações de sala ($CONCURRENT workers x 10) em ${CONC_DURATION}s"

echo ""
echo "======================================"
echo "  RESUMO"
echo "======================================"
echo "Health: $HEALTH_SUCCESSES/$NUM_REQUESTS sucessos, ${HEALTH_AVG}s médio"
echo "Criar: $CREATE_SUCCESSES/$NUM_GAMES sucessos, ${CREATE_AVG}s médio"
echo "Join: $JOIN_SUCCESSES/$NUM_REQUESTS sucessos, ${JOIN_AVG}s médio"
echo "Concorrência: $CONC_REQUESTS criações em ${CONC_DURATION}s"
echo "======================================"
