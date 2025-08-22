# Script PowerShell para parar processos RMI do Grupo2
# Uso: .\scripts\parar-grupo2.ps1

Write-Host "========================================" -ForegroundColor Red
Write-Host "    PARANDO GRUPO2 - PROCESSOS RMI" -ForegroundColor Red
Write-Host "========================================" -ForegroundColor Red
Write-Host ""

# Função para parar processos Java que contenham uma string específica
function Stop-JavaProcesses {
    param(
        [string]$ProcessName
    )
    
    $javaProcesses = Get-WmiObject Win32_Process | Where-Object { 
        $_.Name -eq "java.exe" -and $_.CommandLine -like "*$ProcessName*" 
    }
    
    if ($javaProcesses) {
        foreach ($process in $javaProcesses) {
            Write-Host "Parando processo: $ProcessName (PID: $($process.ProcessId))" -ForegroundColor Yellow
            Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
        }
        return $true
    }
    return $false
}

# Parar ProcessoRMI1
Write-Host "[1/4] Parando ProcessoRMI1..." -ForegroundColor Yellow
$stopped1 = Stop-JavaProcesses "ProcessoRMI1"
if ($stopped1) {
    Write-Host "[1/4] ProcessoRMI1 parado" -ForegroundColor Green
} else {
    Write-Host "[1/4] ProcessoRMI1 não estava rodando" -ForegroundColor Gray
}

# Parar ProcessoRMI2
Write-Host "[2/4] Parando ProcessoRMI2..." -ForegroundColor Yellow
$stopped2 = Stop-JavaProcesses "ProcessoRMI2"
if ($stopped2) {
    Write-Host "[2/4] ProcessoRMI2 parado" -ForegroundColor Green
} else {
    Write-Host "[2/4] ProcessoRMI2 não estava rodando" -ForegroundColor Gray
}

# Parar ProcessoRMI3
Write-Host "[3/4] Parando ProcessoRMI3..." -ForegroundColor Yellow
$stopped3 = Stop-JavaProcesses "ProcessoRMI3"
if ($stopped3) {
    Write-Host "[3/4] ProcessoRMI3 parado" -ForegroundColor Green
} else {
    Write-Host "[3/4] ProcessoRMI3 não estava rodando" -ForegroundColor Gray
}

# Parar RMI Registry (opcional)
Write-Host "[4/4] Verificando RMI Registry..." -ForegroundColor Yellow
$rmiProcess = Get-Process -Name "rmiregistry" -ErrorAction SilentlyContinue
if ($rmiProcess) {
    Write-Host "[4/4] Parando RMI Registry..." -ForegroundColor Yellow
    Stop-Process -Name "rmiregistry" -Force -ErrorAction SilentlyContinue
    Write-Host "[4/4] RMI Registry parado" -ForegroundColor Green
} else {
    Write-Host "[4/4] RMI Registry não estava rodando" -ForegroundColor Gray
}

# Aguardar um momento para os processos terminarem
Start-Sleep -Seconds 2

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "   TODOS OS PROCESSOS FORAM PARADOS!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# Verificar se ainda há processos Java relacionados rodando
$remainingProcesses = Get-WmiObject Win32_Process | Where-Object { 
    $_.Name -eq "java.exe" -and (
        $_.CommandLine -like "*ProcessoRMI1*" -or 
        $_.CommandLine -like "*ProcessoRMI2*" -or 
        $_.CommandLine -like "*ProcessoRMI3*"
    )
}

if ($remainingProcesses) {
    Write-Host "AVISO: Ainda há processos Java relacionados rodando:" -ForegroundColor Yellow
    foreach ($process in $remainingProcesses) {
        Write-Host "- PID: $($process.ProcessId) - $($process.CommandLine)" -ForegroundColor Yellow
    }
    Write-Host ""
    $forceKill = Read-Host "Deseja forçar a parada destes processos? (s/n)"
    if ($forceKill -eq 's' -or $forceKill -eq 'S') {
        foreach ($process in $remainingProcesses) {
            Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
            Write-Host "Processo $($process.ProcessId) forçado a parar" -ForegroundColor Red
        }
    }
} else {
    Write-Host "Todos os processos RMI do Grupo2 foram parados com sucesso!" -ForegroundColor Green
}

Write-Host ""
Read-Host "Pressione Enter para continuar"