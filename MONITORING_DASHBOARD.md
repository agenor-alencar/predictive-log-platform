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

Os testes sao executados com k6 dentro de container Docker.

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

## Fase 2: Execucao Manual por Etapas

### O que e o k6

- `k6` e a ferramenta que gera carga na API
- ele simula usuarios virtuais acessando o endpoint varias vezes
- aqui ele roda dentro de container Docker, sem exigir instalacao local

### O que significam os parametros

- `VUs` = virtual users, ou usuarios virtuais concorrentes
- `iterations` = numero total de execucoes do teste
- `req/s` = requisicoes por segundo
- `5xx` = erros internos do servidor, como 500, 502, 503 e 504
- `latencia media` = tempo medio de resposta
- `P95` = 95% das requisicoes ficaram abaixo desse tempo; os 5% restantes foram mais lentos
- `P99` = mostra os 1% piores casos

### Preparar a rede Docker usada pelo k6

No PowerShell, descubra a rede do compose:

```powershell
$NETWORK = docker network ls --format "{{.Name}}" | Select-String "logplatform-net" | Select-Object -First 1 | ForEach-Object { $_.ToString().Trim() }
$NETWORK
```

O valor esperado e algo como `predictive-log-platform_logplatform-net`.

### Baseline sem carga

Antes de gerar trafego, observe Prometheus e Grafana por 1 minuto.

Voce deve ver:

- `req/s` muito perto de zero
- `5xx` igual a zero
- uso de memoria e conexoes sem picos

### Etapa 1: teste basico manual

Execute este comando e acompanhe os paineis enquanto ele roda:

```powershell
docker run --rm --network $NETWORK -e ITERATIONS=200 -e VUS=20 -e LOGIN_URL=http://java-api:8080/auth/login -e TARGET_URL=http://java-api:8080/predict/error -v ${PWD}\scripts_didaticos\k6_predict_test.js:/scripts/test.js:ro grafana/k6 run /scripts/test.js
```

Objetivo didatico:

- mostrar o comportamento normal do sistema
- comparar depois com o estresse pesado

O que observar:

- `Taxa HTTP (Java API)` sobe um pouco
- `Erros HTTP 5xx (Java API)` permanece em zero
- `Latencia P95 HTTP (Java API)` sobe pouco
- `Conexoes Ativas ao Banco` sobem e voltam

### Pausa para leitura

Depois do teste basico, espere de 30 a 60 segundos olhando os graficos.

Objetivo:

- mostrar recuperacao do sistema
- deixar claro o que e baseline e o que e degradacao

### Etapa 2: teste intermediario manual

Se quiser um meio-termo antes do estresse pesado, use:

```powershell
docker run --rm --network $NETWORK -e ITERATIONS=1000 -e VUS=50 -e LOGIN_URL=http://java-api:8080/auth/login -e TARGET_URL=http://java-api:8080/predict/error -v ${PWD}\scripts_didaticos\k6_predict_test.js:/scripts/test.js:ro grafana/k6 run /scripts/test.js
```

Objetivo didatico:

- mostrar aumento de carga sem ir direto para o limite
- ajudar a turma a perceber tendencia de latencia e conexoes

### Etapa 3: teste de estresse/DDoS didatico

Agora execute o teste pesado:

```powershell
docker run --rm --network $NETWORK -e ITERATIONS=10000 -e VUS=200 -e LOGIN_URL=http://java-api:8080/auth/login -e TARGET_URL=http://java-api:8080/predict/error -v ${PWD}\scripts_didaticos\k6_predict_test.js:/scripts/test.js:ro grafana/k6 run /scripts/test.js
```

Objetivo didatico:

- forcar degradacao observavel
- identificar se o gargalo aparece em latencia, banco, memoria ou erros

### O que cada etapa deve mostrar

- baseline: quase sem trafego, sem erro, sem pico
- teste basico: pequena subida de trafego, sem erro relevante
- teste intermediario: aumento perceptivel de latencia e conexoes
- teste pesado: subida forte de `req/s`, possivel crescimento de `5xx`, P95 maior e sinais de saturacao

### Runner opcional

O arquivo [scripts_didaticos/run_full_test_docker.ps1](c:\projetos\predictive-log-platform\scripts_didaticos\run_full_test_docker.ps1) continua existindo para automacao e coleta de artefatos, mas nao e o caminho principal da aula porque esconde as etapas.

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

- unidade: `req/s` = requisicoes por segundo
- exemplo: `0.0667 req/s` significa aproximadamente `4 requisicoes por minuto`
- abaixo de `1 req/s` = carga muito baixa
- entre `1` e `20 req/s` = carga leve a moderada para demonstracao
- acima de `20 req/s` = ja existe pressao mais clara no ambiente de aula
- queda brusca durante a carga = a API pode estar deixando de responder

### Query 2: Taxa de erros 5xx da API Java

Use:

```promql
sum(rate(http_server_requests_seconds_count{job="plip-java-api",status=~"5.."}[1m]))
```

Como interpretar:

- `0` no teste basico e normal
- qualquer valor acima de `0` durante estresse ja indica degradacao real
- valores persistentes acima de `1 erro/s` merecem destaque em aula
- se continuar alto por muito tempo, a API nao recuperou bem

### Query 3: Latencia media da API Java

Use:

```promql
sum(rate(http_server_requests_seconds_sum{job="plip-java-api"}[1m])) /
sum(rate(http_server_requests_seconds_count{job="plip-java-api"}[1m]))
```

Como interpretar:

- essa query retorna o valor em segundos
- exemplo: `0.3989` significa aproximadamente `399 ms`
- abaixo de `0.1` = muito bom
- entre `0.1` e `0.5` = aceitavel
- acima de `0.5` = comeca a chamar atencao
- acima de `1` = ruim para uso interativo
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
- se o P95 estiver muito acima da media, parte dos usuarios esta sofrendo mais do que a media sugere
- se aparecer `No data`, isso nao significa zero; significa que nao houve serie suficiente para calcular naquele momento

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

2. Descobrir a rede do Docker Compose

```powershell
$NETWORK = docker network ls --format "{{.Name}}" | Select-String "logplatform-net" | Select-Object -First 1 | ForEach-Object { $_.ToString().Trim() }
```

3. Abrir logs e monitoramento

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

4. Abrir Grafana e Prometheus

5. Observar baseline sem carga por cerca de 1 minuto

6. Rodar teste leve

```powershell
docker run --rm --network $NETWORK -e ITERATIONS=200 -e VUS=20 -e LOGIN_URL=http://java-api:8080/auth/login -e TARGET_URL=http://java-api:8080/predict/error -v ${PWD}\scripts_didaticos\k6_predict_test.js:/scripts/test.js:ro grafana/k6 run /scripts/test.js
```

7. Observar no Prometheus:

```promql
sum(rate(http_server_requests_seconds_count{job="plip-java-api"}[1m]))
```

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="plip-java-api"}[1m])))
```

8. Esperar 30 a 60 segundos e comparar a recuperacao

9. Rodar teste pesado

```powershell
docker run --rm --network $NETWORK -e ITERATIONS=10000 -e VUS=200 -e LOGIN_URL=http://java-api:8080/auth/login -e TARGET_URL=http://java-api:8080/predict/error -v ${PWD}\scripts_didaticos\k6_predict_test.js:/scripts/test.js:ro grafana/k6 run /scripts/test.js
```

10. Observar erros e saturacao:

```promql
sum(rate(http_server_requests_seconds_count{job="plip-java-api",status=~"5.."}[1m]))
```

```promql
hikaricp_connections_active{job="plip-java-api"}
```

```promql
jvm_memory_used_bytes{job="plip-java-api",area="heap"} / 1024 / 1024
```

11. Comparar baseline, teste leve e teste pesado

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
$NETWORK = docker network ls --format "{{.Name}}" | Select-String "logplatform-net" | Select-Object -First 1 | ForEach-Object { $_.ToString().Trim() }
docker run --rm --network $NETWORK -e ITERATIONS=200 -e VUS=20 -e LOGIN_URL=http://java-api:8080/auth/login -e TARGET_URL=http://java-api:8080/predict/error -v ${PWD}\scripts_didaticos\k6_predict_test.js:/scripts/test.js:ro grafana/k6 run /scripts/test.js
```

Depois compare:

- Prometheus: taxa, erro, P95, memoria, conexoes
- Grafana: taxa HTTP da Java API, erros 5xx, P95 HTTP, memoria JVM, conexoes ao banco, predicoes por minuto e erros de predicao
