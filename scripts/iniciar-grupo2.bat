@echo off
echo ========================================
echo    INICIANDO GRUPO2 - PROCESSOS RMI
echo ========================================
echo.

echo [1/4] Iniciando RMI Registry na porta 1099...
start "RMI Registry" cmd /k "cd /d %~dp0\.. && rmiregistry 1099"
timeout /t 3 /nobreak >nul

echo [2/4] Compilando projeto...
cd /d %~dp0\..
call mvn clean compile
if %errorlevel% neq 0 (
    echo ERRO: Falha na compilacao!
    pause
    exit /b 1
)

echo [3/4] Iniciando ProcessoRMI1 (porta 1101)...
start "ProcessoRMI1" cmd /k "cd /d %~dp0\.. && mvn exec:java -Dexec.mainClass=br.com.grupo2.ProcessoRMI1 -Dexec.args=1101"
timeout /t 2 /nobreak >nul

echo [4/4] Iniciando ProcessoRMI2 (porta 1102)...
start "ProcessoRMI2" cmd /k "cd /d %~dp0\.. && mvn exec:java -Dexec.mainClass=br.com.grupo2.ProcessoRMI2 -Dexec.args=1102"
timeout /t 2 /nobreak >nul

echo [5/4] Iniciando ProcessoRMI3 (porta 1103)...
start "ProcessoRMI3" cmd /k "cd /d %~dp0\.. && mvn exec:java -Dexec.mainClass=br.com.grupo2.ProcessoRMI3 -Dexec.args=1103"

echo.
echo ========================================
echo   TODOS OS PROCESSOS FORAM INICIADOS!
echo ========================================
echo.
echo Processos RMI ativos:
echo - ProcessoRMI1: rmi://localhost:1101/ProcessoRMI1
echo - ProcessoRMI2: rmi://localhost:1102/ProcessoRMI2  
echo - ProcessoRMI3: rmi://localhost:1103/ProcessoRMI3
echo.
echo RMI Registry: rmi://localhost:1099
echo.
echo Para testar:
echo 1. Use o menu interativo em cada processo
echo 2. Teste eleicao em anel
echo 3. Teste comunicacao RMI entre processos
echo.
pause