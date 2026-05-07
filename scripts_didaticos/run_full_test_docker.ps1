param(
    [int]$BasicIterations = 1000,
    [int]$BasicVus = 50,
    [int]$DdosIterations = 50000,
    [int]$DdosVus = 500,
    [int]$RecoverySeconds = 180
)

$ErrorActionPreference = 'Stop'

function Write-Log {
    param([string]$Message)
    $ts = Get-Date -Format 'HH:mm:ss'
    Write-Host "[$ts] $Message"
}

function Assert-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Comando '$Name' nao encontrado no PATH."
    }
}

function Get-ComposeNetworkName {
    param([string]$RootDir)

    $networks = docker network ls --format "{{.Name}}"
    $candidates = $networks -split "`r?`n" | Where-Object { $_ -match 'logplatform-net$' }

    if ($candidates) {
        return ($candidates | Select-Object -First 1)
    }

    throw "Nao foi possivel detectar a rede Docker do compose (esperado algo terminando com 'logplatform-net')."
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
$K6Script = Join-Path $ScriptDir 'k6_predict_test.js'
$Stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$OutDir = Join-Path $RootDir "logs\test_results_$Stamp"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Write-Log "Validando dependencias..."
Assert-Command 'docker'

Write-Log "Validando servicos no docker compose..."
Set-Location $RootDir
$services = docker compose config --services
foreach ($svc in @('java-api', 'postgres', 'kafka')) {
    if (-not ($services -split "`r?`n" | Where-Object { $_ -eq $svc })) {
        throw "Servico '$svc' nao encontrado em docker-compose.yml"
    }
}

Write-Log "Detectando rede do compose..."
$network = Get-ComposeNetworkName -RootDir $RootDir
Write-Log "Rede detectada: $network"

Write-Log "Salvando snapshot de servicos ativos..."
docker compose ps --services | Out-File -Encoding utf8 (Join-Path $OutDir 'services_running.txt')

function Run-K6Scenario {
    param(
        [string]$Name,
        [int]$Iterations,
        [int]$Vus
    )

    Write-Log "Executando $Name (iterations=$Iterations, vus=$Vus)..."

    $summaryFile = "/results/$($Name)_summary.json"
    $cmd = @(
        'run', '--rm',
        '--network', $network,
        '-e', "ITERATIONS=$Iterations",
        '-e', "VUS=$Vus",
        '-e', 'LOGIN_URL=http://java-api:8080/auth/login',
        '-e', 'TARGET_URL=http://java-api:8080/predict/error',
        '-v', "${OutDir}:/results",
        '-v', "${K6Script}:/scripts/test.js:ro",
        'grafana/k6',
        'run', '--summary-export', $summaryFile, '/scripts/test.js'
    )

    docker @cmd 2>&1 | Tee-Object -FilePath (Join-Path $OutDir "$Name.log")
}

Run-K6Scenario -Name 'basic_test' -Iterations $BasicIterations -Vus $BasicVus

Write-Log "Aguardando recuperacao ($RecoverySeconds s)..."
Start-Sleep -Seconds $RecoverySeconds

Run-K6Scenario -Name 'ddos_test' -Iterations $DdosIterations -Vus $DdosVus

Write-Log "Coletando logs dos servicos..."
docker compose logs java-api | Out-File -Encoding utf8 (Join-Path $OutDir 'java-api.log')
docker compose logs postgres | Out-File -Encoding utf8 (Join-Path $OutDir 'postgres.log')
docker compose logs kafka | Out-File -Encoding utf8 (Join-Path $OutDir 'kafka.log')

Write-Log "Exportando metrica do Prometheus..."
$query = 'rate(http_server_requests_seconds_bucket[1m])'
$uri = "http://localhost:9090/api/v1/query?query=$([uri]::EscapeDataString($query))"
try {
    Invoke-WebRequest -Uri $uri -OutFile (Join-Path $OutDir 'prometheus_query.json') | Out-Null
} catch {
    Write-Log "Aviso: nao foi possivel exportar Prometheus agora."
}

Write-Log "Resumo final:"
Write-Host "- Pasta de saida: $OutDir"
Write-Host "- Log teste basico: $(Join-Path $OutDir 'basic_test.log')"
Write-Host "- Log teste DDoS: $(Join-Path $OutDir 'ddos_test.log')"
Write-Host "- Summaries: basic_test_summary.json / ddos_test_summary.json"
Write-Host "- Logs servicos: java-api.log, postgres.log, kafka.log"
Write-Host "- Prometheus: prometheus_query.json"

Write-Log "Concluido."
