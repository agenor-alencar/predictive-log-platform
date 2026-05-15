# PROJECT_CONTEXT ? Predictive Log Intelligence Platform (PLIP)

> Documento de referência para reengenharia, manutenção evolutiva e padronização arquitetural.
> Toda modificação no sistema deve ser lida à luz deste contexto antes de ser implementada.

---

## 1. VISÃO GERAL DO SISTEMA

A **PLIP** é uma plataforma distribuída de nível enterprise para:

- Ingestão de logs HTTP via upload CSV
- Análise estatística descritiva dos logs
- Inferência preditiva em tempo real (erro e tempo de resposta) via ML
- Detecção de anomalias e monitoramento de data drift
- Observabilidade completa com Prometheus + Grafana + MLflow

---

## 2. STACK ATUAL

| Camada | Tecnologia | Versão |
|---|---|---|
| Backend principal | Java + Spring Boot | 21 LTS / 3.2.3 |
| Serviço de ML | Python + FastAPI | 3.12 / latest |
| Banco de dados relacional | PostgreSQL | 16 |
| Cache em memória | Redis | 7 |
| Mensageria | Apache Kafka (Confluent) | 7.6.0 |
| ORM (Java) | Spring Data JPA / Hibernate | 6.x |
| ORM (Python) | SQLAlchemy + psycopg2 | ? |
| API | REST (ambos os serviços) | ? |
| Documentação API | SpringDoc OpenAPI + FastAPI auto-docs | 2.3.0 / built-in |
| Containerização | Docker + Docker Compose | ? |
| Rastreamento de modelos | MLflow | ? |
| Métricas | Prometheus + Micrometer | 2.51.0 |
| Dashboards | Grafana | 10.4.1 |
| Segurança | Spring Security + JWT (jjwt 0.12.5) | ? |
| CI/CD | GitHub Actions | ? |

---

## 3. ARQUITETURA

**Padrão**: Microserviços (2 serviços principais) + infraestrutura de suporte.

```
[Cliente / Swagger / Postman]
          ?
          ?
???????????????????????????????
?   Java API  :8080           ?  Spring Boot 3.2 ? REST principal
?   Spring Security (JWT)     ?  Autenticação Stateless
?   controller / service /    ?  Arquitetura Hexagonal parcial
?   domain / infrastructure   ?  (ports & adapters incompleto)
???????????????????????????????
             ? WebClient HTTP (reativo)
             ?
???????????????????????????????
?   Python ML Service :8000   ?  FastAPI ? inferência e treinamento
?   routers / models /        ?  Arquitetura em camadas
?   infrastructure / config   ?
???????????????????????????????
             ?
    ???????????????????????????
    ?                         ?
PostgreSQL :5432          MLflow :5000
Redis :6379               Volume /models
Kafka :9092
Prometheus :9090
Grafana :3000
```

### 3.1 Comunicação entre serviços

| De | Para | Protocolo | Descrição |
|---|---|---|---|
| Java API | Python ML | HTTP síncrono (WebClient) | Encaminha predições de erro e tempo de resposta |
| Java API | Kafka | Producer | Publica eventos de log ingeridos |
| Python ML | Kafka | Consumer | Consome eventos para processamento assíncrono |
| Java API | PostgreSQL | JPA/Hibernate | CRUD de logs e predições |
| Python ML | PostgreSQL | SQLAlchemy | Leitura de logs para treinamento |
| Python ML | MLflow | MLflow SDK | Registra experimentos, métricas e artefatos |
| Ambos | Redis | Spring Data Redis | Cache de predições (TTL 5min) e stats (TTL 30s) |
| Ambos | Prometheus | Micrometer / Instrumentator | Exposição de métricas |

---

## 4. ESTRUTURA DE PACOTES ? JAVA API

```
com.logplatform/
??? LogPlatformApplication.java          # Entry point
?
??? controller/                          # Camada de apresentação REST
?   ??? AuthController.java              # POST /auth/login, /auth/register
?   ??? LogController.java               # POST /logs/upload
?   ??? PredictController.java           # POST /predict/error, /predict/response-time
?   ??? StatsController.java             # GET  /stats/summary
?
??? service/                             # Regras de negócio e orquestração
?   ??? LogIngestionService.java         # Parsing CSV, batch save, Kafka produce
?   ??? PredictionService.java           # Orquestração ML + métricas Micrometer
?   ??? StatisticsService.java           # Agregações SQL + Redis cache
?
??? repository/                          # Contratos de persistência (Spring Data JPA)
?   ??? WebLogRepository.java
?   ??? PredictionRepository.java
?
??? entity/                              # Mapeamento JPA ? PostgreSQL
?   ??? WebLog.java                      # Tabela: web_logs
?   ??? Prediction.java                  # Tabela: predictions
?
??? dto/                                 # Objetos de transferência de dados
?   ??? ErrorPredictionRequest.java
?   ??? ErrorPredictionResponse.java
?   ??? ResponseTimePrediction.java
?   ??? LogUploadResponse.java
?   ??? StatsSummary.java
?
??? domain/                              # Núcleo de domínio (Hexagonal ? parcial)
?   ??? model/
?   ?   ??? WebLogDomain.java
?   ?   ??? PredictionResult.java
?   ??? port/
?   ?   ??? LogRepository.java           # Interface de saída (driven port)
?   ?   ??? MlServicePort.java           # Interface de saída para ML
?   ?   ??? PredictionPort.java
?   ??? service/                         # Serviços de domínio puros
?
??? infrastructure/                      # Adaptadores externos (Hexagonal)
?   ??? adapter/                         # Implementações de portas
?   ??? kafka/
?       ??? KafkaConfig.java
?       ??? LogEventProducer.java
?       ??? LogEventConsumer.java
?       ??? LogStreamProcessor.java
?
??? config/                              # Configuração Spring
?   ??? SecurityConfig.java              # JWT + Spring Security
?   ??? WebClientConfig.java             # WebClient para Python ML
?   ??? RedisConfig.java                 # Serialização Redis
?   ??? MetricsConfig.java              # Beans Micrometer / Prometheus
?
??? security/
    ??? JwtTokenProvider.java
    ??? JwtAuthenticationFilter.java
```

### 4.1 Padrão arquitetural identificado (Java)

O projeto Java inicia uma **Arquitetura Hexagonal (Ports & Adapters)** com a separação em `domain/port/` e `infrastructure/adapter/`, mas **não está completamente implementada**: os serviços de aplicação (`service/`) ainda referenciam diretamente os repositórios JPA (`WebLogRepository`, `PredictionRepository`) em vez de usar exclusivamente as interfaces de porta do domínio. Isso é **acoplamento parcial** entre camada de aplicação e infraestrutura.

---

## 5. ESTRUTURA DE MÓDULOS ? PYTHON ML SERVICE

```
python-ml-service/app/
??? main.py                    # Entry point FastAPI
??? config.py                  # Settings (Pydantic BaseSettings)
??? feature_engineering.py     # Transformação de features para inferência
??? dataset_generator.py       # Geração de dataset a partir do PostgreSQL
??? scheduler.py               # Agendamento de tarefas periódicas
?
??? routers/                   # Camada de apresentação FastAPI
?   ??? train.py               # POST /train, POST /generate-dataset
?   ??? predict.py             # POST /predict/error, /predict/response-time
?   ??? anomaly.py             # POST /detect/anomaly
?   ??? monitor.py             # GET /monitor/drift
?   ??? websocket.py           # WebSocket para alertas em tempo real
?
??? models/                    # Lógica de treinamento e inferência
?   ??? classifier.py          # Classificadores (LogReg, RF, XGBoost)
?   ??? regressor.py           # Regressores (resposta de tempo)
?   ??? anomaly.py             # Isolation Forest / detecção de outliers
?
??? infrastructure/            # Conexão com serviços externos
?
??? monitoring/                # Evidently AI ? Data Drift
```

---

## 6. CONTRATOS DA API (NÃO QUEBRAR)

### 6.1 Java API ? Endpoints públicos

| Método | Path | Descrição |
|---|---|---|
| POST | `/auth/login` | Autenticação JWT |
| POST | `/auth/register` | Registro de usuário |
| POST | `/logs/upload` | Upload CSV `multipart/form-data` |
| GET | `/stats/summary` | Resumo estatístico dos logs |
| POST | `/predict/error` | Predição de probabilidade de erro |
| POST | `/predict/response-time` | Predição de tempo de resposta |
| GET | `/actuator/metrics` | Métricas Prometheus |
| GET | `/actuator/health` | Health check |

### 6.2 Python ML Service ? Endpoints públicos

| Método | Path | Descrição |
|---|---|---|
| POST | `/generate-dataset` | Gera dataset a partir do PostgreSQL |
| POST | `/train` | Treina classificador e regressor |
| POST | `/predict/error` | Inferência de erro (chamada pela Java API) |
| POST | `/predict/response-time` | Inferência de tempo de resposta |
| POST | `/detect/anomaly` | Detecção de anomalias em batch |
| GET | `/monitor/drift` | Relatório de data drift (Evidently AI) |
| GET | `/docs` | Swagger UI FastAPI |
| GET | `/metrics` | Prometheus metrics |

### 6.3 DTO principal de entrada ? predição de erro

```json
{
  "method": "GET",
  "hour": 14,
  "historical_avg_response": 250.5,
  "day_of_week": 2
}
```

### 6.4 DTO principal de saída ? predição de erro

```json
{
  "error_probability": 0.12,
  "risk_level": "LOW",
  "model_used": "RandomForestClassifier",
  "inference_time_ms": 3.4
}
```

---

## 7. INCONSISTÊNCIAS E DÉBITO TÉCNICO IDENTIFICADOS

### 7.1 Arquitetura Hexagonal Incompleta (ALTA PRIORIDADE)
- `LogIngestionService` e `PredictionService` injetam diretamente `WebLogRepository` e `PredictionRepository` (interfaces Spring Data JPA) em vez de usar as interfaces de porta em `domain/port/`.
- Isso cria **dependência da camada de aplicação com a camada de infraestrutura**, violando o princípio de inversão de dependência (DIP ? SOLID).
- **Impacto**: dificulta testes unitários puros e troca de implementação de persistência.

### 7.2 Ausência de Mapper dedicado (MÉDIA PRIORIDADE)
- Não há camada de mapper explícita (`WebLog` ? `WebLogDomain` ? `DTO`).
- A conversão pode estar ocorrendo dentro dos services, misturando responsabilidades.
- **Impacto**: viola SRP; dificulta evolução independente de entidades JPA e contratos de API.

### 7.3 Tratamento de erros sem GlobalExceptionHandler (ALTA PRIORIDADE)
- O `LogController` captura exceções manualmente com try/catch.
- Não há evidência de um `@ControllerAdvice` / `@RestControllerAdvice` centralizado.
- **Impacto**: lógica de erro duplicada entre controllers; inconsistência no formato das respostas de erro.

### 7.4 Estado compartilhado no Python via variável global (ALTA PRIORIDADE)
- O `predict.py` importa `classifier_pipeline` diretamente do módulo `train.py` (`from app.routers.train import classifier_pipeline`).
- Isso é **estado global mutável** acoplado entre routers, violando separação de responsabilidades.
- **Impacto**: risco de race condition; dificulta testes isolados; impede escalabilidade horizontal stateless.

### 7.5 Segurança ? CORS aberto no Python ML Service (ALTA PRIORIDADE)
- `allow_origins=["*"]` configurado no middleware CORS do FastAPI.
- O serviço Python não possui autenticação própria.
- **Impacto**: serviço ML exposto sem controle de acesso; recomenda-se restringir a origem para a rede interna Docker apenas.

### 7.6 Ausência de paginação nos endpoints de leitura (MÉDIA PRIORIDADE)
- Endpoints como `GET /stats/summary` podem retornar volumes crescentes de dados sem paginação.
- **Impacto**: risco de `OutOfMemoryError` e degradação de performance em produção.

### 7.7 Ausência de soft delete nas entidades (BAIXA PRIORIDADE)
- `WebLog` e `Prediction` não possuem campo `deleted_at`/`active`.
- **Impacto**: deleção física de logs compromete auditoria e rastreabilidade.

---

## 8. REGRAS OBRIGATÓRIAS PARA EVOLUÇÃO

> Estas regras são inegociáveis para qualquer modificação nesta base de código.

### Antes de implementar qualquer feature:
1. Ler os arquivos relacionados ao módulo que será alterado
2. Identificar padrão existente no módulo
3. Verificar se há DTOs, entities e services envolvidos
4. Checar se há testes existentes para o módulo
5. Avaliar impacto em contratos de API existentes

### NÃO FAZER:
- Criar controllers com lógica de negócio
- Referenciar entidades JPA diretamente em DTOs de resposta
- Usar `any` (Python) ou `Object` não tipado (Java) sem justificativa
- Alterar nomes de campos em DTOs sem versionar o endpoint
- Criar endpoints novos sem documentação Swagger/OpenAPI
- Modificar `SecurityConfig` sem revisar todos os filtros JWT
- Alterar o schema do PostgreSQL sem criar migration versionada
- Compartilhar estado mutável entre routers no Python

### SEMPRE FAZER:
- Separar DTO de entrada (Request) de DTO de saída (Response)
- Usar `@Valid` / `@Validated` em todos os request bodies Java
- Usar `Pydantic BaseModel` com `Field(...)` em todos os schemas Python
- Registrar logs estruturados com `log.info/warn/error` (Java) ou `logger.info/warning/error` (Python)
- Propagar erros até o handler global (Java: `@ControllerAdvice`; Python: exception handlers no `main.py`)
- Escrever ao menos testes unitários para novos services
- Documentar decisões arquiteturais relevantes neste arquivo

---

## 9. PADRÕES DE CÓDIGO

### 9.1 Java ? Estrutura esperada para nova feature

```
controller/NovaFuncionalidadeController.java     ? @RestController, delega para service
service/NovaFuncionalidadeService.java           ? lógica de negócio, usa ports/repos
domain/port/NovaFuncionalidadePort.java          ? interface de porta (se necessário)
infrastructure/adapter/NovaFuncionalidadeAdapter ? implementação da porta
repository/NovaFuncionalidadeRepository.java     ? extends JpaRepository<Entidade, Long>
entity/NovaEntidade.java                         ? @Entity com @Table, Lombok
dto/NovaFuncionalidadeRequest.java               ? record ou class com @Valid
dto/NovaFuncionalidadeResponse.java              ? record ou class sem dados sensíveis
```

### 9.2 Python ? Estrutura esperada para nova feature

```
routers/nova_funcionalidade.py        ? APIRouter, schemas Pydantic, delega para models/
models/nova_funcionalidade.py         ? lógica ML / processamento puro
infrastructure/nova_dependencia.py    ? clientes externos (DB, S3, etc.)
tests/test_nova_funcionalidade.py     ? pytest com fixtures
```

---

## 10. PORTAS E ENDPOINTS DOS SERVIÇOS

| Serviço | Porta | URL local |
|---|---|---|
| Java API | 8080 | http://localhost:8080 |
| Java Swagger | 8080 | http://localhost:8080/swagger-ui.html |
| Python ML | 8000 | http://localhost:8000 |
| Python Docs | 8000 | http://localhost:8000/docs |
| PostgreSQL | 5432 | postgresql://logadmin:logadmin123@localhost:5432/logplatform |
| Redis | 6379 | redis://localhost:6379 |
| Kafka | 9092 | localhost:9092 |
| MLflow | 5000 | http://localhost:5000 |
| Prometheus | 9090 | http://localhost:9090 |
| Grafana | 3000 | http://localhost:3000 |

---

## 11. COMANDOS ESSENCIAIS

```bash
# Subir todos os serviços
docker compose up -d

# Ver logs de um serviço específico
docker compose logs -f java-api
docker compose logs -f python-ml

# Rebuild após mudança de código
docker compose build java-api && docker compose up -d java-api

# Executar testes Java
cd java-api && mvn test

# Executar testes Python
cd python-ml-service && pytest tests/ -v

# Gerar cobertura Java (JaCoCo)
cd java-api && mvn verify
```

---

## 12. FORMATO DE RESPOSTA PARA NOVOS MÓDULOS

Ao solicitar uma nova funcionalidade, a resposta deve sempre seguir esta ordem:

1. **Análise do cenário atual** ? o que existe relacionado ao módulo
2. **Problemas identificados** ? inconsistências e débitos que afetam a feature
3. **Estratégia de implementação** ? abordagem incremental e segura
4. **Estrutura de arquivos** ? lista com responsabilidade de cada arquivo
5. **Dependências necessárias** ? novas libs, configs ou migrations
6. **Código** ? completo, tipado, sem simplificações
7. **Possíveis riscos** ? regressões, performance, segurança
8. **Melhorias futuras** ? débitos que ficam para próxima iteração
