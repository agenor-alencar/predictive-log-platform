param(
    [int]$BasicRequests = 1000,
    [int]$DdosRequests = 50000,
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

function Resolve-PythonCommand {
    $candidates = @(
        @{ Cmd = 'py'; Args = @('-3') },
        @{ Cmd = 'python'; Args = @() }
    )

    foreach ($candidate in $candidates) {
        $cmd = $candidate.Cmd
        $args = $candidate.Args
        if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
            continue
        }

        try {
            $versionOutput = & $cmd @args --version 2>&1
            if ($LASTEXITCODE -eq 0 -and (($versionOutput | Out-String) -match 'Python\s+\d')) {
                return $candidate
            }
        } catch {
            # Tenta proximo candidato
        }
    }

    throw "Nao foi encontrado um Python executavel. Instale Python 3 e/ou use o launcher 'py -3'. Se estiver usando alias da Microsoft Store, desative em Settings > Apps > Advanced app settings > App execution aliases."
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
$Simulator = Join-Path $ScriptDir 'crash_api_simulator.py'
$Stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$OutDir = Join-Path $RootDir "logs\test_results_$Stamp"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Write-Log "Validando dependencias..."
Assert-Command 'docker'
$pythonCommand = Resolve-PythonCommand
Write-Log "Usando interpretador Python: $($pythonCommand.Cmd) $($pythonCommand.Args -join ' ')"

Write-Log "Validando servicos no docker compose..."
Set-Location $RootDir
$services = docker compose config --services
foreach ($svc in @('java-api', 'postgres', 'kafka')) {
    if (-not ($services -split "`r?`n" | Where-Object { $_ -eq $svc })) {
        throw "Servico '$svc' nao encontrado em docker-compose.yml"
    }
}

Write-Log "Salvando snapshot de servicos ativos..."
docker compose ps --services | Out-File -Encoding utf8 (Join-Path $OutDir 'services_running.txt')

Write-Log "Executando teste basico ($BasicRequests req)..."
$env:NUM_REQUESTS = "$BasicRequests"
$pythonRunArgs = @() + $pythonCommand.Args + @($Simulator)
& $pythonCommand.Cmd @pythonRunArgs 2>&1 | Tee-Object -FilePath (Join-Path $OutDir 'basic_test.log')

Write-Log "Aguardando recuperacao ($RecoverySeconds s)..."
Start-Sleep -Seconds $RecoverySeconds

Write-Log "Executando teste DDoS ($DdosRequests req)..."
$env:NUM_REQUESTS = "$DdosRequests"
& $pythonCommand.Cmd @pythonRunArgs 2>&1 | Tee-Object -FilePath (Join-Path $OutDir 'ddos_test.log')

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
Write-Host "- Logs servicos: java-api.log, postgres.log, kafka.log"
Write-Host "- Prometheus: prometheus_query.json"

Write-Log "Concluido."
