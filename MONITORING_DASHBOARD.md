# Guia Pratico: Testar API Java com Monitoramento em Tempo Real

Sequencia completa: preparar -> teste basico -> observar baseline -> teste de estresse -> comparar -> analisar.

Este guia foi ajustado para a aula funcionar sem Python instalado no host. O fluxo oficial usa apenas Docker, PowerShell, Prometheus e Grafana.

---

## Estrategia da Aula

Voce vai executar dois cenarios:

1. Teste basico
   Objetivo: gerar trafego controlado e observar o comportamento normal.

2. Teste de estresse/DDoS didatico
   Objetivo: aumentar muito a carga para identificar degradacao, erros e gargalos.

Os testes sao executados com k6 dentro de container Docker, usando o runner:
[scripts_didaticos/run_full_test_docker.ps1](c:\projetos\predictive-log-platform\scripts_didaticos\run_full_test_docker.ps1)

O script de carga usado pelo k6 esta em:
[scripts_didaticos/k6_predict_test.js](c:\projetos\predictive-log-platform\scripts_didaticos\k6_predict_test.js)

---

## Fase 1: Preparacao

### Pre-requisitos

- Docker Desktop em execucao
- Containers da plataforma iniciados com `docker compose up -d`
- Grafana acessivel em `http://localhost:3000`
- Prometheus acessivel em `http://localhost:9090`

### Validacao rapida

No PowerShell, rode:

```powershell
docker compose ps
```

Voce deve ver os servicos `java-api`, `postgres`, `kafka`, `prometheus`, `grafana`, `python-ml` e demais containers em estado `Up`.

### Abra 4 terminais

Terminal 1: logs da API Java

```powershell
docker compose logs -f java-api
```

Terminal 2: logs do PostgreSQL

```powershell
docker compose logs -f postgres
```

Terminal 3: logs do Kafka filtrados

```powershell
docker compose logs -f kafka | Select-String -Pattern 'error|warn'
```

Terminal 4: uso de CPU/memoria do container Java

```powershell
while ($true) { docker stats plip-java-api --no-stream; Start-Sleep -Seconds 2 }
```

Pare esse monitor com `Ctrl+C` quando quiser.

### Abra 2 abas no navegador

1. Grafana: `http://localhost:3000`
   Login padrao: `admin` / `admin`

2. Prometheus: `http://localhost:9090`

---

## Fase 2: Execucao Oficial dos Testes

### Caminho oficial da aula

Rode o fluxo completo com o script Docker-first:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts_didaticos\run_full_test_docker.ps1
```

Esse comando executa automaticamente:

1. teste basico
2. pausa de recuperacao
3. teste de estresse/DDoS didatico
4. coleta de logs
5. export de metricas do Prometheus

### Execucao mais leve para demonstracao rapida

Se quiser uma execucao mais curta em sala:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts_didaticos\run_full_test_docker.ps1 -BasicIterations 200 -BasicVus 20 -DdosIterations 2000 -DdosVus 100 -RecoverySeconds 15
```

### Execucao intermediaria

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts_didaticos\run_full_test_docker.ps1 -BasicIterations 1000 -BasicVus 50 -DdosIterations 10000 -DdosVus 200 -RecoverySeconds 30
```

### O que o script gera

Ao final, os artefatos ficam em uma pasta como:

```text
logs/test_results_YYYYMMDD_HHMMSS/
```

Arquivos principais:

- `basic_test.log`
- `ddos_test.log`
- `basic_test_summary.json`
- `ddos_test_summary.json`
- `java-api.log`
- `postgres.log`
- `kafka.log`
- `prometheus_query.json`

---

## Fase 3: O Que os Alunos Devem Observar

### No teste basico

Comportamento esperado:

- requisicoes completam sem erros relevantes
- latencia permanece baixa ou moderada
- CPU do container Java sobe pouco
- memoria JVM cresce pouco
- conexoes do banco sobem, mas voltam rapido ao normal

Indicadores esperados:

- erro HTTP perto de `0%`
- latencia P95 bem menor que no teste pesado
- conexoes ativas do Hikari sem saturar

### No teste de estresse/DDoS didatico

Comportamento esperado:

- aumento forte de latencia
- crescimento das conexoes ativas do banco
- aumento da memoria JVM
- possivel aumento de erros HTTP 5xx
- quedas temporarias, timeouts ou recusas de conexao

Indicadores esperados:

- P95 e P99 sobem bastante
- `hikaricp_connections_active` se aproxima do limite
- logs do Java podem mostrar timeout e falha de conexao
- logs do PostgreSQL podem mostrar saturacao

---

## Fase 4: Como Ler o Prometheus

O Prometheus deste projeto coleta metricas de:

- API Java em `/actuator/prometheus`
- servico Python ML em `/metrics`
- o proprio Prometheus

Arquivo de configuracao:
[prometheus/prometheus.yml](c:\projetos\predictive-log-platform\prometheus\prometheus.yml)

### Query 1: Taxa de requisicoes HTTP da API Java

Use:

```promql
sum(rate(http_server_requests_seconds_count{job="plip-java-api"}[1m]))
```

Como interpretar:

- valor baixo no teste basico = comportamento normal
- valor muito alto no teste pesado = carga chegando na API
- queda brusca durante a carga = a API pode estar deixando de responder

### Query 2: Taxa de erros 5xx da API Java

Use:

```promql
sum(rate(http_server_requests_seconds_count{job="plip-java-api",status=~"5.."}[1m]))
```

Como interpretar:

- `0` no teste basico e normal
- aumento no teste pesado indica degradacao real
- se continuar alto por muito tempo, a API nao recuperou bem

### Query 3: Latencia media da API Java

Use:

```promql
sum(rate(http_server_requests_seconds_sum{job="plip-java-api"}[1m])) /
sum(rate(http_server_requests_seconds_count{job="plip-java-api"}[1m]))
```

Como interpretar:

- valor baixo no teste basico = normal
- crescimento continuo no teste pesado = fila, banco ou CPU saturando

### Query 4: P95 da API Java

Use:

```promql
histogram_quantile(
  0.95,
  sum by (le) (rate(http_server_requests_seconds_bucket{job="plip-java-api"}[1m]))
)
```

Como interpretar:

- P95 mostra a experiencia dos piores 5% das requisicoes
- se a media parece boa mas o P95 explode, ha cauda de latencia

### Query 5: Memoria JVM da API Java

Use:

```promql
jvm_memory_used_bytes{job="plip-java-api",area="heap"} / 1024 / 1024
```

Como interpretar:

- crescimento leve no teste basico = esperado
- crescimento agressivo no teste pesado = pressao de memoria
- se nao cair apos a carga, investigue vazamento ou GC insuficiente

### Query 6: Conexoes ativas do banco

Use:

```promql
hikaricp_connections_active{job="plip-java-api"}
```

Como interpretar:

- sobe durante o teste e deve cair depois
- se ficar colado no maximo, o banco/pool virou gargalo

### Query 7: Requisicoes do servico Python ML

Use:

```promql
sum(rate(http_requests_total{job="plip-python-ml"}[1m]))
```

Como interpretar:

- ajuda a ver se o Python ML tambem esta sendo pressionado
- crescimento nessa metrica mostra trafego chegando ao microservico Python

---

## Fase 5: Como Ler o Grafana

Dashboard usado:
[grafana/dashboards/plip-overview.json](c:\projetos\predictive-log-platform\grafana\dashboards\plip-overview.json)

Importante: o dashboard atual ainda mistura metricas da API Java e do servico Python ML, mas agora ja inclui os principais sinais HTTP da API Java para a aula.

### Painel: Servicos Ativos

O que mostra:

- metrica `up`
- jobs monitorados pelo Prometheus

Como interpretar:

- `UP` = o endpoint de metricas respondeu
- `DOWN` = o Prometheus nao conseguiu coletar a metrica

### Painel: Uso de Memoria JVM (Java API)

O que mostra:

- `jvm_memory_used_bytes`
- `jvm_memory_max_bytes`

Como interpretar:

- `Heap Used` subindo com carga = esperado
- se aproximar demais do `Heap Max` = risco de GC intenso ou OOM

### Painel: Taxa HTTP (Java API)

O que mostra:

- taxa agregada de requisicoes por segundo da API Java

Como interpretar:

- sobe no teste basico e sobe muito mais no teste pesado
- se cair de forma brusca durante carga, a API pode estar saturando ou deixando de responder

### Painel: Erros HTTP 5xx (Java API)

O que mostra:

- taxa de respostas 5xx por segundo da API Java

Como interpretar:

- zero ou proximo de zero no teste basico = esperado
- crescimento no teste pesado = falha funcional sob estresse

### Painel: Latencia P95 HTTP (Java API)

O que mostra:

- P95 agregado da latencia HTTP da API Java em milissegundos

Como interpretar:

- representa os 5% piores tempos de resposta
- se explode enquanto a media ainda parece aceitavel, existe cauda de latencia importante

### Painel: Trafego HTTP (Java API)

O que mostra:

- req/s da API Java
- 5xx/s da API Java

Como interpretar:

- ajuda a comparar volume e erro no mesmo grafico
- req/s subindo junto com 5xx/s indica degradacao sob carga

### Painel: Latencia HTTP (Java API)

O que mostra:

- latencia media da API Java
- latencia P95 da API Java

Como interpretar:

- a media mostra a tendencia geral
- o P95 mostra a pior experiencia dos usuarios sob carga

### Painel: Conexoes Ativas ao Banco (Java API)

O que mostra:

- `hikaricp_connections_active`
- `hikaricp_connections`

Como interpretar:

- ativo subindo durante teste = normal
- ativo proximo do total por muito tempo = pool saturando

### Painel: Predicoes por Minuto (Java API)

O que mostra:

- `rate(ml_predictions_total_total[5m]) * 60`
- `rate(ml_predictions_errors_total[5m]) * 60`

Como interpretar:

- subida durante o teste = o fluxo de predicao esta sendo exercitado
- erros por minuto subindo = degradacao funcional

### Painel: Total de Predicoes

O que mostra:

- contador acumulado de predicoes

Como interpretar:

- deve crescer ao longo dos testes
- se nao crescer, a carga nao esta chegando ao endpoint de predicao

### Painel: Erros de Predicao

O que mostra:

- contador acumulado de falhas de predicao

Como interpretar:

- zero ou proximo de zero no teste basico = esperado
- crescimento no teste pesado = alvo de investigacao

### Paineis do Python ML

O dashboard tambem tem paineis do servico Python:

- Requisicoes por Segundo (Python ML)
- Latencia de Inferencia ML (ms)
- Requisicoes HTTP por Status (Python ML)
- Latencia HTTP por Endpoint (Python ML)

Como interpretar em aula:

- eles mostram o efeito indireto da carga sobre o servico Python
- servem para explicar o encadeamento entre API Java e microservico ML
- complementam a leitura dos novos paineis HTTP da API Java e das queries do Prometheus

---

## Fase 6: Passo a Passo para os Alunos

### Roteiro resumido

1. Subir containers

```powershell
docker compose up -d
```

2. Abrir logs e monitoramento

```powershell
docker compose logs -f java-api
```

```powershell
docker compose logs -f postgres
```

```powershell
docker compose logs -f kafka | Select-String -Pattern 'error|warn'
```

```powershell
while ($true) { docker stats plip-java-api --no-stream; Start-Sleep -Seconds 2 }
```

3. Abrir Grafana e Prometheus

4. Rodar teste leve

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts_didaticos\run_full_test_docker.ps1 -BasicIterations 200 -BasicVus 20 -DdosIterations 1 -DdosVus 1 -RecoverySeconds 1
```

5. Observar no Prometheus:

```promql
sum(rate(http_server_requests_seconds_count{job="plip-java-api"}[1m]))
```

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="plip-java-api"}[1m])))
```

6. Rodar teste pesado

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts_didaticos\run_full_test_docker.ps1 -BasicIterations 1 -BasicVus 1 -DdosIterations 10000 -DdosVus 200 -RecoverySeconds 5
```

7. Observar erros e saturacao:

```promql
sum(rate(http_server_requests_seconds_count{job="plip-java-api",status=~"5.."}[1m]))
```

```promql
hikaricp_connections_active{job="plip-java-api"}
```

```promql
jvm_memory_used_bytes{job="plip-java-api",area="heap"} / 1024 / 1024
```

8. Comparar resultados entre teste leve e teste pesado

---

## Fase 7: O Que Concluir com os Resultados

### Se a taxa de requisicoes sobe e a latencia permanece baixa

Conclusao:

- a API suportou bem a carga observada

### Se a taxa de requisicoes sobe, mas o P95 explode

Conclusao:

- existe degradacao sob carga, mesmo que nem todas as requisicoes falhem

### Se os erros 5xx aumentam

Conclusao:

- a aplicacao entrou em falha funcional sob estresse

### Se as conexoes Hikari saturam

Conclusao:

- o banco ou o pool de conexoes e um gargalo importante

### Se a memoria JVM sobe muito e nao volta

Conclusao:

- ha forte pressao de memoria ou possivel vazamento

### Se o Python ML tambem degrada

Conclusao:

- a cadeia inteira da predicao foi afetada, nao apenas a API Java

---

## Observacoes Importantes para a Aula

- Este roteiro nao depende de Python instalado no Windows.
- O gerador de carga roda em container Docker com k6.
- O warning sobre `version` no `docker-compose.yml` nao bloqueia a aula; e apenas um aviso.
- O dashboard do Grafana agora mostra taxa HTTP, 5xx e P95 da API Java. Use o Prometheus para aprofundar a analise e conferir as queries manualmente.

---

## Proximo Passo

Comece por aqui:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts_didaticos\run_full_test_docker.ps1 -BasicIterations 200 -BasicVus 20 -DdosIterations 2000 -DdosVus 100 -RecoverySeconds 15
```

Depois compare:

- Prometheus: taxa, erro, P95, memoria, conexoes
- Grafana: taxa HTTP da Java API, erros 5xx, P95 HTTP, memoria JVM, conexoes ao banco, predicoes por minuto e erros de predicao
