# Script PowerShell para iniciar processos RMI do Grupo2
# Uso: .\scripts\iniciar-grupo2.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "    INICIANDO GRUPO2 - PROCESSOS RMI" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Navegar para o diretório do projeto
$projectDir = Split-Path -Parent $PSScriptRoot
Set-Location $projectDir

Write-Host "[1/5] Verificando se RMI Registry está rodando..." -ForegroundColor Yellow
$rmiProcess = Get-Process -Name "rmiregistry" -ErrorAction SilentlyContinue
if (-not $rmiProcess) {
    Write-Host "[1/5] Iniciando RMI Registry na porta 1099..." -ForegroundColor Yellow
    Start-Process -FilePath "rmiregistry" -ArgumentList "1099" -WindowStyle Minimized
    Start-Sleep -Seconds 3
} else {
    Write-Host "[1/5] RMI Registry já está rodando" -ForegroundColor Green
}

Write-Host "[2/5] Compilando projeto..." -ForegroundColor Yellow
$compileResult = & mvn clean compile
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERRO: Falha na compilação!" -ForegroundColor Red
    Read-Host "Pressione Enter para sair"
    exit 1
}
Write-Host "[2/5] Compilação concluída com sucesso" -ForegroundColor Green

Write-Host "[3/5] Iniciando ProcessoRMI1 (porta 1101)..." -ForegroundColor Yellow
Start-Process -FilePath "powershell" -ArgumentList "-Command", "cd '$projectDir'; mvn exec:java -Dexec.mainClass=br.com.grupo2.ProcessoRMI1; Read-Host 'Pressione Enter para fechar'"
Start-Sleep -Seconds 2

Write-Host "[4/5] Iniciando ProcessoRMI2 (porta 1102)..." -ForegroundColor Yellow
Start-Process -FilePath "powershell" -ArgumentList "-Command", "cd '$projectDir'; mvn exec:java -Dexec.mainClass=br.com.grupo2.ProcessoRMI2; Read-Host 'Pressione Enter para fechar'"
Start-Sleep -Seconds 2

Write-Host "[5/5] Iniciando ProcessoRMI3 (porta 1103)..." -ForegroundColor Yellow
Start-Process -FilePath "powershell" -ArgumentList "-Command", "cd '$projectDir'; mvn exec:java -Dexec.mainClass=br.com.grupo2.ProcessoRMI3; Read-Host 'Pressione Enter para fechar'"
Start-Sleep -Seconds 2

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "   TODOS OS PROCESSOS FORAM INICIADOS!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Processos RMI ativos:" -ForegroundColor White
Write-Host "- ProcessoRMI1: rmi://localhost:1101/ProcessoRMI1" -ForegroundColor Cyan
Write-Host "- ProcessoRMI2: rmi://localhost:1102/ProcessoRMI2" -ForegroundColor Cyan
Write-Host "- ProcessoRMI3: rmi://localhost:1103/ProcessoRMI3" -ForegroundColor Cyan
Write-Host ""
Write-Host "RMI Registry: rmi://localhost:1099" -ForegroundColor Magenta
Write-Host ""
Write-Host "Para testar:" -ForegroundColor Yellow
Write-Host "1. Use o menu interativo em cada processo" -ForegroundColor White
Write-Host "2. Teste eleição em anel" -ForegroundColor White
Write-Host "3. Teste comunicação RMI entre processos" -ForegroundColor White
Write-Host ""
Write-Host "Para parar todos os processos, execute: .\scripts\parar-grupo2.ps1" -ForegroundColor Yellow
Write-Host ""
Read-Host "Pressione Enter para continuar"