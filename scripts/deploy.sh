#!/bin/bash
# ============================================================
# 🚀 PLIP — Script de Deploy Manual
# ============================================================
# Uso: ./scripts/deploy.sh
#
# Este script pode ser executado diretamente na VM para
# fazer deploy manual (sem depender do GitHub Actions).
# ============================================================

set -euo pipefail

PROJECT_DIR="${PLIP_DIR:-$HOME/predictive-log-platform}"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"
WAIT_SECONDS=30

# Cores para output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()  { echo -e "${BLUE}[INFO]${NC}  $1"; }
ok()   { echo -e "${GREEN}[OK]${NC}    $1"; }
fail() { echo -e "${RED}[FAIL]${NC}  $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $1"; }

echo ""
echo "========================================="
echo " 🚀 PLIP — Deploy Manual"
echo " $(date)"
echo "========================================="
echo ""

# -----------------------------------------------
# 1. Verificar pré-requisitos
# -----------------------------------------------
log "Verificando pré-requisitos..."

if ! command -v docker &> /dev/null; then
  fail "Docker não encontrado. Instale com: curl -fsSL https://get.docker.com | sh"
  exit 1
fi

if ! command -v docker compose &> /dev/null && ! command -v docker-compose &> /dev/null; then
  fail "Docker Compose não encontrado."
  exit 1
fi

if ! command -v git &> /dev/null; then
  fail "Git não encontrado."
  exit 1
fi

ok "Pré-requisitos OK"

# -----------------------------------------------
# 2. Verificar diretório do projeto
# -----------------------------------------------
if [ ! -d "$PROJECT_DIR" ]; then
  warn "Diretório $PROJECT_DIR não encontrado."
  log "Clonando repositório..."
  git clone https://github.com/${GITHUB_REPO:-"seu-usuario/predictive-log-platform"}.git "$PROJECT_DIR"
fi

cd "$PROJECT_DIR"
log "Diretório: $(pwd)"

# -----------------------------------------------
# 3. Atualizar código
# -----------------------------------------------
log "Atualizando código..."

CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "main" ]; then
  warn "Branch atual: $CURRENT_BRANCH (esperado: main)"
fi

git fetch origin main
git reset --hard origin/main
ok "Código atualizado para $(git log -1 --pretty=format:'%h — %s')"

# -----------------------------------------------
# 4. Parar serviços existentes
# -----------------------------------------------
log "Parando serviços existentes..."
docker compose down --remove-orphans 2>/dev/null || true
ok "Serviços parados"

# -----------------------------------------------
# 5. Build e iniciar
# -----------------------------------------------
log "Construindo e iniciando serviços..."
docker compose up --build -d

# -----------------------------------------------
# 6. Aguardar estabilização
# -----------------------------------------------
log "Aguardando $WAIT_SECONDS segundos para estabilização..."
sleep "$WAIT_SECONDS"

# -----------------------------------------------
# 7. Health checks
# -----------------------------------------------
echo ""
log "Executando health checks..."
echo ""

FAILED=0

check_service() {
  local name=$1
  local url=$2

  if curl -sf --max-time 5 "$url" > /dev/null 2>&1; then
    ok "$name"
  else
    fail "$name"
    FAILED=$((FAILED + 1))
  fi
}

check_docker() {
  local name=$1
  local container=$2
  local cmd=$3

  if docker exec "$container" $cmd > /dev/null 2>&1; then
    ok "$name"
  else
    fail "$name"
    FAILED=$((FAILED + 1))
  fi
}

check_docker  "PostgreSQL"        "plip-postgres"  "pg_isready -U logadmin -d logplatform"
check_service "Python ML Service" "http://localhost:8000/health"
check_service "Java API"          "http://localhost:8080/actuator/health"
check_service "MLflow"            "http://localhost:5000"
check_service "Prometheus"        "http://localhost:9090/-/healthy"
check_service "Grafana"           "http://localhost:3000/api/health"

echo ""

# -----------------------------------------------
# 8. Relatório final
# -----------------------------------------------
echo "📊 Status dos containers:"
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || docker compose ps

echo ""

if [ $FAILED -eq 0 ]; then
  echo "========================================="
  ok "Deploy concluído com sucesso! 🎉"
  echo "========================================="
  echo ""
  echo "  🌐 API Java:    http://$(hostname -I | awk '{print $1}'):8080"
  echo "  🌐 Swagger UI:  http://$(hostname -I | awk '{print $1}'):8080/swagger-ui.html"
  echo "  🐍 Python ML:   http://$(hostname -I | awk '{print $1}'):8000/docs"
  echo "  📊 MLflow:      http://$(hostname -I | awk '{print $1}'):5000"
  echo "  📈 Grafana:     http://$(hostname -I | awk '{print $1}'):3000"
  echo "  🔥 Prometheus:  http://$(hostname -I | awk '{print $1}'):9090"
  echo ""
else
  echo "========================================="
  fail "Deploy concluído com $FAILED serviço(s) com problema!"
  echo "========================================="
  echo ""
  echo "Verifique os logs com:"
  echo "  docker compose logs <nome-do-servico>"
  echo ""
  exit 1
fi

# -----------------------------------------------
# 9. Limpeza
# -----------------------------------------------
log "Limpando imagens não utilizadas..."
docker image prune -f > /dev/null 2>&1
ok "Limpeza concluída"
