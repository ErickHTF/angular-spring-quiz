#!/bin/bash

# Load test rápido usando curl
# Uso: ./quick-test.sh [base_url] [num_requests]

BASE_URL="${1:-http://localhost:8080}"
NUM_REQUESTS="${2:-50}"
CONCURRENT="${3:-10}"

echo "======================================"
echo "  Load Test Rápido - fresh-quiz-ng"
echo "======================================"
echo "URL: $BASE_URL"
echo "Requisições: $NUM_REQUESTS"
echo "Concorrentes: $CONCURRENT"
echo "======================================"

# Função para testar criar jogo
test_create_game() {
    local result=$(curl -s -o /dev/null -w "%{http_code},%{time_total}" \
        -X POST "$BASE_URL/api/games" \
        -H "Content-Type: application/json" \
        -d "{\"nickname\":\"LoadTest$(shuf -i 1-9999 -n 1)\"}")
    echo "$result"
}

# Função para testar join
test_join_game() {
    local code=$1
    local result=$(curl -s -o /dev/null -w "%{http_code},%{time_total}" \
        -X POST "$BASE_URL/api/games/$code/join" \
        -H "Content-Type: application/json" \
        -d "{\"nickname\":\"Player$(shuf -i 1-9999 -n 1)\"}")
    echo "$result"
}

# Função para testar health
test_health() {
    local result=$(curl -s -o /dev/null -w "%{http_code},%{time_total}" \
        "$BASE_URL/health")
    echo "$result"
}

echo ""
echo "[1/4] Testando health endpoint..."
echo "--------------------------------------"

HEALTH_SUCCESSES=0
HEALTH_FAILURES=0
HEALTH_TOTAL_TIME=0

for i in $(seq 1 $NUM_REQUESTS); do
    result=$(test_health)
    status=$(echo $result | cut -d',' -f1)
    time=$(echo $result | cut -d',' -f2)
    
    if [ "$status" = "200" ]; then
        HEALTH_SUCCESSES=$((HEALTH_SUCCESSES + 1))
    else
        HEALTH_FAILURES=$((HEALTH_FAILURES + 1))
    fi
    
    HEALTH_TOTAL_TIME=$(echo "$HEALTH_TOTAL_TIME + $time" | bc)
    
    # Progresso
    if [ $((i % 10)) -eq 0 ]; then
        echo "  $i/$NUM_REQUESTS completados..."
    fi
done

HEALTH_AVG=$(echo "scale=4; $HEALTH_TOTAL_TIME / $NUM_REQUESTS" | bc)
echo ""
echo "  Resultados Health:"
echo "  Sucessos: $HEALTH_SUCCESSES/$NUM_REQUESTS"
echo "  Falhas: $HEALTH_FAILURES/$NUM_REQUESTS"
echo "  Tempo médio: ${HEALTH_AVG}s"

echo ""
echo "[2/4] Testando criar jogos..."
echo "--------------------------------------"

CREATE_SUCCESSES=0
CREATE_FAILURES=0
CREATE_TOTAL_TIME=0
GAME_CODES=()

for i in $(seq 1 $((NUM_REQUESTS / 5))); do
    result=$(test_create_game)
    status=$(echo $result | cut -d',' -f1)
    time=$(echo $result | cut -d',' -f2)
    
    if [ "$status" = "200" ]; then
        CREATE_SUCCESSES=$((CREATE_SUCCESSES + 1))
    else
        CREATE_FAILURES=$((CREATE_FAILURES + 1))
    fi
    
    CREATE_TOTAL_TIME=$(echo "$CREATE_TOTAL_TIME + $time" | bc)
done

CREATE_AVG=$(echo "scale=4; $CREATE_TOTAL_TIME / (NUM_REQUESTS / 5)" | bc)
echo "  Sucessos: $CREATE_SUCCESSES"
echo "  Falhas: $CREATE_FAILURES"
echo "  Tempo médio: ${CREATE_AVG}s"

echo ""
echo "[3/4] Testando entrar em jogos..."
echo "--------------------------------------"

JOIN_SUCCESSES=0
JOIN_FAILURES=0
JOIN_TOTAL_TIME=0

for i in $(seq 1 $NUM_REQUESTS); do
    # Usar código aleatório (a maioria não existirá)
    RANDOM_CODE=$(cat /dev/urandom | LC_ALL=C tr -dc 'A-Z0-9' | head -c 4)
    result=$(test_join_game "$RANDOM_CODE")
    status=$(echo $result | cut -d',' -f1)
    time=$(echo $result | cut -d',' -f2)
    
    if [ "$status" = "200" ] || [ "$status" = "404" ]; then
        JOIN_SUCCESSES=$((JOIN_SUCCESSES + 1))
    else
        JOIN_FAILURES=$((JOIN_FAILURES + 1))
    fi
    
    JOIN_TOTAL_TIME=$(echo "$JOIN_TOTAL_TIME + $time" | bc)
done

JOIN_AVG=$(echo "scale=4; $JOIN_TOTAL_TIME / $NUM_REQUESTS" | bc)
echo "  Sucessos: $JOIN_SUCCESSES/$NUM_REQUESTS"
echo "  Falhas: $JOIN_FAILURES/$NUM_REQUESTS"
echo "  Tempo médio: ${JOIN_AVG}s"

echo ""
echo "[4/4] Teste de concorrência..."
echo "--------------------------------------"

CONC_START=$(date +%s.%N)

for i in $(seq 1 $CONCURRENT); do
    (
        for j in $(seq 1 10); do
            curl -s -o /dev/null \
                -X POST "$BASE_URL/api/games" \
                -H "Content-Type: application/json" \
                -d "{\"nickname\":\"Concurrent$i-$j\"}" &
        done
    ) &
done

wait

CONC_END=$(date +%s.%N)
CONC_DURATION=$(echo "$CONC_END - $CONC_START" | bc)

echo "  $CONCURRENT conexões concorrentes completadas em ${CONC_DURATION}s"

echo ""
echo "======================================"
echo "  RESUMO"
echo "======================================"
echo "Health: $HEALTH_SUCCESSES sucessos, ${HEALTH_AVG}s médio"
echo "Criar: $CREATE_SUCCESSES sucessos"
echo "Join: $JOIN_SUCCESSES sucessos, ${JOIN_AVG}s médio"
echo "Concorrência: ${CONC_DURATION}s para $CONCURRENT conexões"
echo "======================================"
