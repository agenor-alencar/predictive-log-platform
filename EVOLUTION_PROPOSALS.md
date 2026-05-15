# EVOLUTION PROPOSALS ? Predictive Log Intelligence Platform (PLIP)

> Propostas de evolução organizadas em módulos independentes para trabalho em duplas.
> Cada módulo tem escopo delimitado, arquivos afetados mapeados e critérios de aceitação claros.
> Os módulos são ordenados por **dependência técnica**: resolva débitos de base antes de features novas.

---

## COMO USAR ESTE DOCUMENTO

- Cada módulo é autossuficiente: uma dupla pode pegá-lo sem bloquear outra
- Leia o `PROJECT_CONTEXT.md` antes de iniciar qualquer módulo
- Siga o fluxo do `ENGINEERING_GUIDE.md` antes de codar
- Ao concluir um módulo, marque o status e registre decisões no `PROJECT_CONTEXT.md` (seção 12)

---

## ÍNDICE DE MÓDULOS

| ID | Módulo | Camada | Prioridade | Pré-requisito |
|---|---|---|---|---|
| M-01 | GlobalExceptionHandler | Java API | CRÍTICA | ? |
| M-02 | ModelRegistry Python | Python ML | CRÍTICA | ? |
| M-03 | Mapper Layer (Java) | Java API | ALTA | M-01 |
| M-04 | CORS + Network Isolation | Infra / Python | ALTA | ? |
| M-05 | Paginação de Logs e Predições | Java API | ALTA | M-01, M-03 |
| M-06 | Soft Delete nas Entidades | Java API | MÉDIA | M-03 |
| M-07 | Rate Limiting na API Java | Java API | MÉDIA | M-01 |
| M-08 | Filtros Avançados de Estatísticas | Java API + Python | MÉDIA | M-05 |
| M-09 | Health Checks Detalhados | Infra / Java | MÉDIA | ? |
| M-10 | Testes de Integração Java | Java API | ALTA | M-01, M-03 |
| M-11 | Testes Python ? Cobertura Router | Python ML | ALTA | M-02 |
| M-12 | Endpoint de Histórico de Predições | Java API | BAIXA | M-05 |
| M-13 | Re-treino Automático Agendado | Python ML | BAIXA | M-02 |
| M-14 | Alertas via WebSocket | Python ML | BAIXA | M-02 |

---

---

## M-01 ? GlobalExceptionHandler (Java API)

**Prioridade**: CRÍTICA
**Dupla**: Desenvolvimento backend Java
**Esforço estimado**: pequeno (1 sessão)

### Contexto

Atualmente cada controller captura exceções individualmente com `try/catch` manual:

```java
// LogController.java ? padrão atual (repetido em 3 controllers)
try {
    ...
} catch (IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(...);
} catch (Exception e) {
    log.error("Upload failed", e);
    return ResponseEntity.internalServerError().body(...);
}
```

Isso causa: duplicação de código, respostas de erro com formatos diferentes por controller, ausência de tratamento de erros de validação Bean Validation (`@Valid`), e impossibilidade de centralizar log de exceções.

### Objetivo

Criar um handler global `@RestControllerAdvice` que padronize **todas** as respostas de erro da API, e remover os `try/catch` dos controllers após a criação.

### Arquivos a criar

```
java-api/src/main/java/com/logplatform/
??? exception/
    ??? GlobalExceptionHandler.java   ? @RestControllerAdvice
    ??? ApiErrorResponse.java         ? DTO padrão de erro
    ??? BusinessException.java        ? exceção de domínio base
    ??? ResourceNotFoundException.java ? 404 semântico
```

### Arquivos a modificar

```
controller/LogController.java       ? remover try/catch, simplificar para 1 linha
controller/PredictController.java   ? remover try/catch, simplificar para 1 linha
controller/StatsController.java     ? sem alteração (não tem try/catch)
```

### Contrato do DTO de erro (não alterar após definido)

```json
{
  "timestamp": "2026-05-14T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "File must be a CSV file",
  "path": "/logs/upload"
}
```

### Critérios de aceitação

- [ ] `GET /stats/summary` com banco vazio retorna `200` com dados zerados (comportamento atual preservado)
- [ ] `POST /logs/upload` com arquivo não-CSV retorna `400` com body no formato `ApiErrorResponse`
- [ ] `POST /logs/upload` com arquivo válido continua retornando `200` (sem regressão)
- [ ] `POST /predict/error` com body inválido retorna `400` com mensagem de validação
- [ ] Nenhum controller contém `try/catch` após o refactor
- [ ] Testes unitários dos controllers passam sem alteração de lógica

---

---

## M-02 ? ModelRegistry Centralizado (Python ML)

**Prioridade**: CRÍTICA
**Dupla**: Desenvolvimento Python / ML
**Esforço estimado**: pequeno-médio (1?2 sessões)

### Contexto

O `predict.py` acessa o modelo treinado via importação direta de variável do módulo `train.py`:

```python
# predict.py ? problema atual
from app.routers.train import classifier_pipeline  # estado global mutável
```

Isso cria acoplamento entre routers, risco de race condition em requisições concorrentes, e impede testes isolados.

O projeto já possui `app/infrastructure/model_registry.py` ? mas ainda não é usado consistentemente por todos os routers.

### Objetivo

Garantir que `ModelRegistry` seja a **única fonte de verdade** dos modelos carregados, eliminar todas as importações de variáveis globais entre routers, e validar com testes.

### Arquivos a verificar/completar

```
python-ml-service/app/
??? infrastructure/
?   ??? model_registry.py       ? verificar implementação atual e completar se necessário
??? routers/
?   ??? predict.py              ? substituir import global por ModelRegistry.instance()
?   ??? anomaly.py              ? idem
?   ??? train.py                ? garantir que persiste modelos via registry após treino
```

### Padrão esperado após o módulo

```python
# predict.py ? padrão correto
from app.infrastructure.model_registry import ModelRegistry

@router.post("/predict/error", response_model=ErrorPredictionResponse)
async def predict_error(request: ErrorPredictionRequest):
    registry = ModelRegistry.instance()
    classifier = registry.get_classifier()
    if classifier is None:
        raise HTTPException(status_code=503, detail="Modelo não treinado.")
    ...
```

### Critérios de aceitação

- [ ] Nenhum router importa variáveis diretamente de outro router
- [ ] `POST /predict/error` sem modelo treinado retorna `503` com mensagem clara
- [ ] `POST /train` seguido de `POST /predict/error` funciona corretamente
- [ ] `ModelRegistry` é singleton (não recria instância a cada request)
- [ ] Testes em `test_predict.py` não precisam mais do `fixture` que altera `train.classifier_pipeline` diretamente

---

---

## M-03 ? Camada de Mapper (Java API)

**Prioridade**: ALTA
**Dupla**: Desenvolvimento backend Java
**Pré-requisito**: M-01 concluído
**Esforço estimado**: médio (2 sessões)

### Contexto

Atualmente a conversão entre `WebLog` (entity JPA) e DTOs pode estar ocorrendo dentro dos services ou de forma inline. Não existe camada de mapper explícita, o que viola SRP e acopla o contrato de API ao modelo de banco.

### Objetivo

Criar mappers dedicados para cada entidade, desacoplando a evolução do schema do banco da evolução dos contratos de API.

### Arquivos a criar

```
java-api/src/main/java/com/logplatform/
??? mapper/
    ??? WebLogMapper.java         ? WebLog ? WebLogDomain ? DTO
    ??? PredictionMapper.java     ? Prediction ? PredictionResult ? DTO
```

### Convenção do mapper

Mappers são classes `@Component` com métodos estáticos ou de instância. **Não usar MapStruct neste momento** para não adicionar dependência desnecessária ? implementar manualmente seguindo o padrão existente do projeto.

```java
@Component
public class WebLogMapper {

    public WebLogDomain toDomain(WebLog entity) { ... }

    public WebLog toEntity(WebLogDomain domain) { ... }

    public WebLogResponse toResponse(WebLogDomain domain) { ... }
}
```

### Critérios de aceitação

- [ ] `LogIngestionService` não manipula campos de `WebLog` diretamente em lógica de negócio
- [ ] `StatisticsService` não retorna campos de entity JPA nos DTOs de resposta
- [ ] Mappers possuem testes unitários com casos de campo nulo
- [ ] Nenhum campo de `WebLog` (ex: `createdAt` interno) vaza para DTOs de resposta pública

---

---

## M-04 ? CORS Restrito + Isolamento de Rede (Infra / Python)

**Prioridade**: ALTA
**Dupla**: Infraestrutura / segurança
**Esforço estimado**: pequeno (1 sessão)

### Contexto

O Python ML Service tem CORS aberto (`allow_origins=["*"]`) e não possui autenticação. Em produção, o serviço deve ser acessível **apenas** pela Java API dentro da rede Docker.

### Objetivo

1. Restringir CORS no Python para aceitar apenas origens conhecidas
2. Garantir que o serviço Python não seja exposto publicamente no `docker-compose.yml`
3. Adicionar validação de `X-Internal-Request` header para bloquear chamadas diretas externas

### Arquivos a modificar

```
python-ml-service/app/main.py        ? ajustar allow_origins para variável de ambiente
python-ml-service/app/config.py      ? adicionar CORS_ORIGINS: list[str]
docker-compose.yml                   ? remover bind 0.0.0.0 do python-ml (porta só interna)
```

### Configuração esperada

```python
# config.py
CORS_ORIGINS: list[str] = Field(
    default=["http://java-api:8080", "http://localhost:8080"],
    description="Allowed CORS origins"
)
```

```python
# main.py
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,  # não mais ["*"]
    allow_credentials=True,
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)
```

### Critérios de aceitação

- [ ] `docker compose up` sobe todos os serviços sem erro
- [ ] Java API consegue chamar `POST /predict/error` no Python normalmente
- [ ] Porta `8000` não está vinculada a `0.0.0.0` no ambiente de produção (apenas interna Docker)
- [ ] Variável `CORS_ORIGINS` sobrepõe o default via variável de ambiente no `docker-compose.yml`

---

---

## M-05 ? Paginação de Logs e Predições (Java API)

**Prioridade**: ALTA
**Dupla**: Desenvolvimento backend Java
**Pré-requisito**: M-01, M-03
**Esforço estimado**: médio (2 sessões)

### Contexto

Não há endpoint para listar logs ou histórico de predições com paginação. O `StatisticsService` faz `webLogRepository.count()` e queries de agregação ? sem risco de OOM. Mas futuras queries de listagem sem paginação representam risco real.

### Objetivo

Criar endpoints de listagem paginada para `WebLog` e `Prediction`, usando `Pageable` do Spring Data.

### Arquivos a criar

```
controller/LogQueryController.java      ? GET /logs?page=0&size=20&sort=timestamp,desc
controller/PredictionQueryController.java ? GET /predictions?page=0&size=20
dto/PagedResponse.java                   ? wrapper genérico de paginação
dto/WebLogResponse.java                  ? resposta pública de um log (sem createdAt interno)
dto/PredictionResponse.java              ? resposta pública de uma predição
```

### Arquivos a modificar

```
repository/WebLogRepository.java       ? adicionar query paginada com filtros
repository/PredictionRepository.java   ? idem
```

### Contrato esperado

```
GET /logs?page=0&size=20&sort=timestamp,desc&method=GET&statusCode=500
```

```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 1500,
  "totalPages": 75,
  "last": false
}
```

### Critérios de aceitação

- [ ] `GET /logs` sem parâmetros retorna página 0 com 20 itens
- [ ] `GET /logs?size=200` é rejeitado com `400` (size máximo = 100)
- [ ] Filtro por `method` e `statusCode` funciona corretamente
- [ ] `GET /predictions?page=1&size=10` retorna a segunda página
- [ ] Nenhuma query carrega todos os registros em memória
- [ ] Endpoint documentado no Swagger com exemplos

---

---

## M-06 ? Soft Delete nas Entidades (Java API)

**Prioridade**: MÉDIA
**Dupla**: Desenvolvimento backend Java
**Pré-requisito**: M-03
**Esforço estimado**: pequeno (1 sessão)

### Contexto

`WebLog` e `Prediction` não possuem campo de deleção lógica. Qualquer `DELETE` remove o dado fisicamente, comprometendo auditoria.

### Objetivo

Adicionar `deletedAt` e `@SQLRestriction` (Hibernate 6) para que queries automáticas do Spring Data excluam registros deletados logicamente.

### Arquivos a modificar

```
entity/WebLog.java          ? adicionar LocalDateTime deletedAt
entity/Prediction.java      ? idem
repository/WebLogRepository.java     ? adicionar deleteById lógico
repository/PredictionRepository.java ? idem
```

### Migration SQL necessária

```sql
-- Adicionar em postgres/migrations/V2__add_soft_delete.sql
ALTER TABLE web_logs ADD COLUMN deleted_at TIMESTAMP DEFAULT NULL;
ALTER TABLE predictions ADD COLUMN deleted_at TIMESTAMP DEFAULT NULL;
```

### Padrão esperado na entidade

```java
@SQLRestriction("deleted_at IS NULL")   // Hibernate 6 ? filtra automaticamente queries
@Column(name = "deleted_at")
private LocalDateTime deletedAt;
```

### Critérios de aceitação

- [ ] `DELETE /logs/{id}` preenche `deleted_at` sem remover o registro
- [ ] `GET /logs` não retorna registros com `deleted_at` preenchido
- [ ] Contagem estatística (`/stats/summary`) exclui registros deletados
- [ ] Registro físico permanece no banco para auditoria

---

---

## M-07 ? Rate Limiting na API Java

**Prioridade**: MÉDIA
**Dupla**: Segurança / backend Java
**Pré-requisito**: M-01
**Esforço estimado**: pequeno-médio (1?2 sessões)

### Contexto

Não há proteção contra abuso de endpoints. Um cliente pode fazer upload de CSV ilimitado ou esgotar o serviço ML com predições em loop.

### Objetivo

Implementar rate limiting por IP usando `Bucket4j` com Redis como backend distribuído (compatível com múltiplas instâncias).

### Dependência a adicionar no `pom.xml`

```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.10.1</version>
</dependency>
```

### Arquivos a criar

```
config/RateLimitConfig.java            ? configuração dos buckets por endpoint
infrastructure/adapter/RateLimitFilter.java ? OncePerRequestFilter com lógica de bucket
exception/RateLimitExceededException.java   ? 429 Too Many Requests
```

### Limites esperados

| Endpoint | Limite | Janela |
|---|---|---|
| `POST /logs/upload` | 5 req | por IP / minuto |
| `POST /predict/error` | 30 req | por IP / minuto |
| `POST /predict/response-time` | 30 req | por IP / minuto |
| `POST /auth/login` | 10 req | por IP / minuto |

### Critérios de aceitação

- [ ] 6ª chamada ao `/logs/upload` em 1 minuto retorna `429`
- [ ] Header `X-RateLimit-Remaining` presente em todas as respostas
- [ ] Header `X-RateLimit-Reset` indica quando o bucket recarrega
- [ ] Limites configuráveis via `application.yml` (não hardcoded)
- [ ] Rate limit reseta corretamente após a janela de tempo

---

---

## M-08 ? Filtros Avançados de Estatísticas (Java + Python)

**Prioridade**: MÉDIA
**Dupla**: Desenvolvimento fullstack (Java + Python)
**Pré-requisito**: M-05
**Esforço estimado**: médio (2?3 sessões)

### Contexto

`GET /stats/summary` retorna um resumo estático de **todos** os logs. Não é possível filtrar por período, método HTTP ou faixa de status code.

### Objetivo

Adicionar suporte a filtros de período e dimensões nas estatísticas, sem quebrar o endpoint atual.

### Parte Java ? Novo endpoint (não substituir o atual)

```
GET /stats/summary?from=2026-05-01&to=2026-05-14&method=GET
```

Arquivos a criar/modificar:
```
dto/StatsFilterRequest.java         ? parâmetros de filtro (query params)
dto/StatsSummary.java               ? adicionar campo period (opcional)
repository/WebLogRepository.java    ? queries JPQL com filtros opcionais
service/StatisticsService.java      ? overload de computeSummary(StatsFilterRequest)
```

### Parte Python ? Estatísticas por segmento

```
GET /stats/by-hour         ? distribuição por hora do dia
GET /stats/by-method       ? agrupamento por método HTTP
GET /stats/by-status-class ? 2xx, 3xx, 4xx, 5xx
```

Arquivos a criar:
```
python-ml-service/app/routers/stats.py    ? novo router
```

### Critérios de aceitação

- [ ] `GET /stats/summary` sem filtro continua funcionando (sem regressão)
- [ ] `GET /stats/summary?from=2026-05-01&to=2026-05-14` retorna apenas dados do período
- [ ] `GET /stats/summary?method=POST` filtra corretamente
- [ ] Cache Redis invalida quando filtros são diferentes (chave inclui parâmetros)
- [ ] Python retorna distribuição por hora do dia corretamente

---

---

## M-09 ? Health Checks Detalhados (Infra / Java)

**Prioridade**: MÉDIA
**Dupla**: Infraestrutura / SRE
**Esforço estimado**: pequeno (1 sessão)

### Contexto

`/actuator/health` retorna `{"status": "UP"}` genérico. Não indica saúde individual de dependências (PostgreSQL, Redis, Kafka, Python ML).

### Objetivo

Criar health indicators customizados para cada dependência, expondo um endpoint rico que o Grafana e o Prometheus possam usar para alertas de degradação parcial.

### Arquivos a criar

```
config/HealthConfig.java                         ? registrar beans de health
infrastructure/health/
    ??? MlServiceHealthIndicator.java            ? chama GET /health do Python
    ??? KafkaHealthIndicator.java                ? verifica conexão com broker
    ??? RedisHealthIndicator.java                ? ping Redis
```

### Resposta esperada

```json
GET /actuator/health

{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL 16" } },
    "redis": { "status": "UP" },
    "kafka": { "status": "UP", "details": { "topics": ["log.events"] } },
    "mlService": { "status": "UP", "details": { "url": "http://python-ml:8000" } },
    "diskSpace": { "status": "UP" }
  }
}
```

### Critérios de aceitação

- [ ] Quando Python ML está down, `mlService.status` é `DOWN` mas `overall.status` é `DEGRADED` (não `DOWN`)
- [ ] Quando Kafka está down, o serviço Java continua funcionando com `kafka.status: DOWN`
- [ ] Endpoint documentado e acessível sem autenticação
- [ ] Timeout de verificação do Python ML: máximo 2s para não travar o health check

---

---

## M-10 ? Testes de Integração Java (Testcontainers)

**Prioridade**: ALTA
**Dupla**: Qualidade / backend Java
**Pré-requisito**: M-01, M-03
**Esforço estimado**: médio-grande (2?3 sessões)

### Contexto

Existem testes de controller com `@WebMvcTest` mas sem banco real. O `IntegrationTest.java` existe mas não está completo. Não há testes que verifiquem o fluxo completo: upload CSV ? salvar banco ? consultar estatísticas.

### Objetivo

Criar testes de integração com Testcontainers (PostgreSQL + Redis) que cubram os fluxos críticos end-to-end na camada de API Java.

### Arquivos a criar

```
src/test/java/com/logplatform/
??? integration/
?   ??? LogIngestionIntegrationTest.java    ? upload CSV ? verifica banco
?   ??? StatisticsIntegrationTest.java      ? ingestão ? computeSummary()
?   ??? PredictionAuditIntegrationTest.java ? predição salva no banco
??? fixture/
?   ??? WebLogFixture.java                 ? factory de objetos de teste
?   ??? CsvFixture.java                    ? CSVs válidos e inválidos para teste
??? config/
    ??? TestContainersConfig.java           ? @SpringBootTest + Testcontainers
```

### Dependências já presentes no `pom.xml`

```xml
<!-- testcontainers.version=1.19.5 já declarada no pom.xml -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

### Critérios de aceitação

- [ ] `mvn test` executa todos os testes de integração sem banco local instalado
- [ ] Fluxo completo: upload de `data/web_logs.csv` ? `count()` > 0 no banco de teste
- [ ] `computeSummary()` após ingestão retorna `totalRecords > 0`
- [ ] Upload de arquivo não-CSV retorna `400` via integração real
- [ ] Cobertura de linha dos services >= 70% (JaCoCo)

---

---

## M-11 ? Cobertura de Testes Python (Routers Críticos)

**Prioridade**: ALTA
**Dupla**: Qualidade / Python ML
**Pré-requisito**: M-02
**Esforço estimado**: médio (2 sessões)

### Contexto

Existem `test_predict.py`, `test_anomaly.py` e `test_pipeline.py`, mas os testes dependem de importar `train.classifier_pipeline` diretamente (estado global). Após M-02, os testes precisam ser adaptados. Também não há testes para os routers `monitor.py` e `websocket.py`.

### Objetivo

Adaptar testes existentes para usar `ModelRegistry`, adicionar testes para `monitor.py` e garantir cobertura >= 80% nos routers críticos.

### Arquivos a criar/modificar

```
python-ml-service/tests/
??? test_predict.py       ? adaptar para ModelRegistry (remover acesso a train.*)
??? test_anomaly.py       ? idem
??? test_pipeline.py      ? idem
??? test_monitor.py       ? NOVO: testa drift detection com dados mock
??? test_train.py         ? NOVO: testa pipeline de treino isolado
??? conftest.py           ? NOVO: fixtures compartilhadas (modelos treinados, DB mock)
```

### Padrão de fixture esperado

```python
# conftest.py
@pytest.fixture(scope="session")
def trained_registry():
    """Registry com modelos treinados para reuso entre testes."""
    registry = ModelRegistry.instance()
    df = generate_synthetic_dataset(n_records=500, seed=42)
    # ... treino ...
    return registry
```

### Critérios de aceitação

- [ ] `pytest tests/ -v` executa sem acessar banco real (mocks de DB)
- [ ] Nenhum teste acessa `train.classifier_pipeline` diretamente
- [ ] `test_monitor.py` testa `GET /monitor/drift` com dados de referência e dados atuais
- [ ] Cobertura >= 80% nos routers `predict.py`, `train.py` e `anomaly.py`
- [ ] `pytest --cov=app tests/` reporta cobertura

---

---

## M-12 ? Endpoint de Histórico de Predições (Java API)

**Prioridade**: BAIXA
**Dupla**: Desenvolvimento backend Java
**Pré-requisito**: M-05
**Esforço estimado**: pequeno (1 sessão)

### Contexto

A tabela `predictions` já existe no banco com `input_data` (JSONB) e `result` (JSONB), e o `PredictionService` já salva cada predição. Não há endpoint para consultar esse histórico.

### Objetivo

Expor o histórico de predições com paginação e filtro por tipo (`error` / `response-time`).

### Arquivos a criar

```
controller/PredictionQueryController.java   ? GET /predictions
dto/PredictionResponse.java                 ? resposta pública (sem dados sensíveis internos)
```

### Contrato esperado

```
GET /predictions?type=error&page=0&size=20
```

```json
{
  "content": [
    {
      "id": 1,
      "predictionType": "error",
      "inputData": { "method": "GET", "hour": 14, ... },
      "result": { "errorProbability": 0.12, "riskLevel": "LOW" },
      "modelVersion": "v1.2.3",
      "latencyMs": 3.4,
      "createdAt": "2026-05-14T10:30:00"
    }
  ],
  "page": 0,
  "totalElements": 230
}
```

### Critérios de aceitação

- [ ] `GET /predictions` retorna lista paginada
- [ ] Filtro `?type=error` retorna apenas predições de erro
- [ ] `inputData` e `result` são desserializados corretamente do JSONB
- [ ] Endpoint requer autenticação JWT
- [ ] Documentado no Swagger com exemplos de resposta

---

---

## M-13 ? Re-treino Automático Agendado (Python ML)

**Prioridade**: BAIXA
**Dupla**: ML Engineering / Python
**Pré-requisito**: M-02
**Esforço estimado**: médio (2 sessões)

### Contexto

O `scheduler.py` existe mas não está integrado ao pipeline de re-treino. O modelo só é retreinado quando alguém chama `POST /train` manualmente.

### Objetivo

Configurar o scheduler para disparar `POST /train` automaticamente quando:
1. O volume de novos dados desde o último treino ultrapassar um threshold configurável
2. O drift detectado pelo Evidently AI ultrapassar um score configurável

### Arquivos a modificar/criar

```
python-ml-service/app/
??? scheduler.py                        ? completar com APScheduler jobs
??? infrastructure/
?   ??? retrain_trigger.py              ? lógica de decisão de re-treino
??? config.py                           ? RETRAIN_THRESHOLD_RECORDS, DRIFT_THRESHOLD_SCORE
```

### Critérios de aceitação

- [ ] Scheduler inicia junto com a aplicação FastAPI (via `lifespan`)
- [ ] Verificação de threshold acontece a cada N minutos (configurável)
- [ ] Re-treino não bloqueia a API durante execução (task assíncrona)
- [ ] Log estruturado registra motivo do re-treino (`"reason": "drift_detected"`)
- [ ] Re-treino manual via `POST /train` continua funcionando normalmente

---

---

## M-14 ? Alertas via WebSocket (Python ML)

**Prioridade**: BAIXA
**Dupla**: Fullstack / Python
**Pré-requisito**: M-02
**Esforço estimado**: médio (2 sessões)

### Contexto

O `websocket.py` existe mas os alertas não são disparados automaticamente quando anomalias são detectadas ou quando o drift ultrapassa thresholds.

### Objetivo

Fazer o WebSocket emitir alertas em tempo real quando:
- Uma predição retorna `risk_level == "CRITICAL"`
- O score de drift ultrapassa `0.3`
- Um batch de anomalias detecta > 5% de outliers

### Arquivos a modificar

```
python-ml-service/app/
??? routers/websocket.py    ? implementar broadcast de alertas
??? routers/predict.py      ? publicar alerta em CRITICAL predictions
??? routers/anomaly.py      ? publicar alerta quando outlier rate > threshold
??? routers/monitor.py      ? publicar alerta quando drift score > threshold
```

### Critérios de aceitação

- [ ] Cliente WebSocket conectado em `ws://localhost:8000/ws/alerts` recebe mensagem quando predição é CRITICAL
- [ ] Formato da mensagem: `{"type": "alert", "level": "CRITICAL", "source": "predict_error", "timestamp": "...", "detail": {...}}`
- [ ] Clientes desconectados não causam erro no servidor
- [ ] WebSocket não bloqueia o pipeline de predição (fire-and-forget)

---

---

## ATRIBUIÇÃO DAS 5 DUPLAS ? 3 SPRINTS PARALELOS

> Regra: **toda dupla termina o sprint atual antes de avançar para o próximo.**
> Sprint 1 é a base de todos ? ninguém avança sem ele estar pronto.

| Dupla | Sprint 1 ? Fundação (todos fazem) | Sprint 2 ? Construção | Sprint 3 ? Features |
|---|---|---|---|
| **1** | M-01 GlobalExceptionHandler + M-02 ModelRegistry Python | M-07 Rate Limiting | M-13 Re-treino Automático |
| **2** | M-01 GlobalExceptionHandler + M-02 ModelRegistry Python | M-03 Mapper Layer | M-10 Testes Integração Java |
| **3** | M-01 GlobalExceptionHandler + M-02 ModelRegistry Python | M-04 CORS + Network | M-14 Alertas WebSocket |
| **4** | M-01 GlobalExceptionHandler + M-02 ModelRegistry Python | M-09 Health Checks + M-06 Soft Delete | M-05 Paginação |
| **5** | M-01 GlobalExceptionHandler + M-02 ModelRegistry Python | M-11 Testes Python | M-08 Filtros Estatísticas + M-12 Histórico de Predições |

> Sprint 1 é igual para todas as duplas: as duas entregas base sem as quais nada mais funciona.
