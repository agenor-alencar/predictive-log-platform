#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   bash scripts_didaticos/run_full_test.sh
# Optional env vars:
#   BASIC_REQUESTS=1000 DDOS_REQUESTS=50000 RECOVERY_SECONDS=180

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SIMULATOR="$ROOT_DIR/scripts_didaticos/crash_api_simulator.py"
STAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/logs/test_results_$STAMP"
BASIC_REQUESTS="${BASIC_REQUESTS:-1000}"
DDOS_REQUESTS="${DDOS_REQUESTS:-50000}"
RECOVERY_SECONDS="${RECOVERY_SECONDS:-180}"

mkdir -p "$OUT_DIR"

log() {
  echo "[$(date +%H:%M:%S)] $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Erro: comando '$1' n\u00e3o encontrado no PATH." >&2
    exit 1
  }
}

log "Validando depend\u00eancias..."
require_cmd docker
require_cmd python

log "Checando servi\u00e7os do docker compose..."
cd "$ROOT_DIR"
SERVICES="$(docker compose config --services)"
for s in java-api postgres kafka; do
  echo "$SERVICES" | grep -qx "$s" || {
    echo "Erro: servi\u00e7o '$s' n\u00e3o existe no docker-compose.yml" >&2
    exit 1
  }
done

log "Checando containers ativos..."
docker compose ps --services > "$OUT_DIR/services_running.txt"

log "Executando teste b\u00e1sico (${BASIC_REQUESTS} req)..."
NUM_REQUESTS="$BASIC_REQUESTS" python "$SIMULATOR" | tee "$OUT_DIR/basic_test.log"

log "Aguardando recupera\u00e7\u00e3o (${RECOVERY_SECONDS}s)..."
sleep "$RECOVERY_SECONDS"

log "Executando teste DDoS (${DDOS_REQUESTS} req)..."
NUM_REQUESTS="$DDOS_REQUESTS" python "$SIMULATOR" | tee "$OUT_DIR/ddos_test.log"

log "Coletando logs dos servi\u00e7os..."
docker compose logs java-api > "$OUT_DIR/java-api.log" || true
docker compose logs postgres > "$OUT_DIR/postgres.log" || true
docker compose logs kafka > "$OUT_DIR/kafka.log" || true

log "Exportando m\u00e9trica do Prometheus..."
PROM_QUERY='rate(http_server_requests_seconds_bucket[1m])'
ENC_QUERY="$(python - <<'PY'
import urllib.parse
print(urllib.parse.quote('rate(http_server_requests_seconds_bucket[1m])', safe=''))
PY
)"
PROM_URL="http://localhost:9090/api/v1/query?query=${ENC_QUERY}"
curl -sS "$PROM_URL" -o "$OUT_DIR/prometheus_query.json" || true

log "Resumo final:"
echo "- Pasta de sa\u00edda: $OUT_DIR"
echo "- Log teste b\u00e1sico: $OUT_DIR/basic_test.log"
echo "- Log teste DDoS: $OUT_DIR/ddos_test.log"
echo "- Logs servi\u00e7os: $OUT_DIR/{java-api,postgres,kafka}.log"
echo "- Prometheus: $OUT_DIR/prometheus_query.json"

log "Conclu\u00eddo."
