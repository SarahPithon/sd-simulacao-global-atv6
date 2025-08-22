# Sistema Distribuído Multigrupo

## Descrição

Este projeto implementa uma **simulação completa de sistema distribuído** com arquitetura híbrida, demonstrando conceitos fundamentais de sistemas distribuídos através de uma implementação prática e robusta.

### Objetivos do Projeto

- Demonstrar diferentes paradigmas de comunicação distribuída (gRPC vs RMI)
- Implementar algoritmos clássicos de eleição (Bully e Anel)
- Aplicar conceitos de sincronização temporal com Relógios de Lamport
- Desenvolver mecanismos de tolerância a falhas e recuperação
- Criar um sistema de snapshot global consistente
- Implementar controle de acesso e autenticação distribuída

### Características Principais

- **Arquitetura Multigrupo**: Dois grupos independentes com tecnologias distintas
- **Comunicação Híbrida**: gRPC, RMI, TCP Sockets e UDP Multicast
- **Tolerância a Falhas**: Detecção automática e reeleição
- **Snapshot Global**: Algoritmo Chandy-Lamport para consistência
- **Interface Interativa**: Terminal com menu completo de funcionalidades
- **Logging Distribuído**: Monitoramento detalhado de todos os eventos

## Arquitetura do Sistema

### Grupos de Processos

#### Grupo A (gRPC + Bully)

- **Processos**: Processo1, Processo2, Processo3
- **Portas**: 8081, 8082, 8083 (gRPC) + 9001, 9002, 9003 (TCP)
- **Tecnologia**: gRPC com Protocol Buffers
- **Eleição**: Algoritmo Bully (baseado em ID)
- **Características**: Comunicação tipada, serialização binária eficiente

#### Grupo B (RMI + Anel)

- **Processos**: ProcessoRMI1, ProcessoRMI2, ProcessoRMI3
- **Portas**: 1099, 1100, 1101 (RMI) + 8091, 8092, 8093 (gRPC)
- **Tecnologia**: Java RMI (Remote Method Invocation)
- **Eleição**: Algoritmo de Anel (topologia circular)
- **Características**: Transparência de localização, chamadas remotas como locais

### Camadas de Comunicação

#### Comunicação Intra-grupo

- **Protocolo**: TCP Sockets
- **Características**: Confiável, ordenada, controle de fluxo
- **Uso**: Mensagens entre processos do mesmo grupo
- **Implementação**: ObjectInputStream/ObjectOutputStream para serialização

#### Comunicação Inter-grupos

- **Protocolo**: UDP Multicast
- **Endereço**: 224.0.0.1:12345
- **Características**: Broadcast eficiente, baixa latência
- **Uso**: Coordenação entre grupos, eleição de supercoordenador

#### Sincronização Temporal

- **Algoritmo**: Relógios Lógicos de Lamport
- **Implementação**: Cada processo mantém contador local
- **Atualização**: max(local, recebido) + 1 a cada evento
- **Objetivo**: Ordenação causal de eventos distribuídos

### Funcionalidades Principais

#### 1. Eleição Distribuída

**Algoritmo Bully (Grupo A)**

- **Critério**: ID do processo (maior ID vence)
- **Mensagens**: ELEICAO, ELEICAO_OK, NOVO_LIDER
- **Timeout**: 5 segundos para resposta
- **Processo**: Detecta falha → Envia ELEICAO → Aguarda OK → Torna-se líder ou aguarda

**Algoritmo de Anel (Grupo B)**

- **Critério**: Parâmetro de eleição (10, 20, 30)
- **Topologia**: Anel lógico ProcessoRMI1 → ProcessoRMI2 → ProcessoRMI3 → ProcessoRMI1
- **Implementação**: Híbrida (RMI + Sockets TCP)
- **Processo**: Inicia eleição → Circula mensagem → Determina líder → Anuncia resultado

**Supercoordenador**

- **Função**: Coordenação global entre grupos
- **Eleição**: Entre líderes dos grupos via multicast
- **Responsabilidades**: Snapshot global, coordenação inter-grupos

#### 2. Snapshot Global (Chandy-Lamport)

- **Iniciador**: Supercoordenador
- **Portas**: 60000 + ID do processo (ex: Processo1 = 60001)
- **Algoritmo**: Marcadores de snapshot, captura de estado e canais
- **Resultado**: Estado consistente global de todos os processos
- **Implementação**: SnapshotManager com servidores dedicados

#### 3. Detecção de Falhas e Heartbeat

**Grupo A (gRPC)**

- **Método**: Ping/Pong via gRPC
- **Frequência**: A cada 3 segundos
- **Timeout**: 15 segundos
- **Ação**: Reeleição automática após detecção

**Grupo B (RMI)**

- **Método**: Heartbeat via RMI + verificação de conectividade
- **Monitoramento**: Processo seguinte no anel
- **Timeout**: 15 segundos
- **Recuperação**: Reconstrução automática do anel

## Estrutura do Projeto

### Organização dos Módulos

```
sd-simulacao-global-atv6/
├── README.md                           # Documentação principal
├── CONFIGURACAO_ANEL_RMI.md           # Configuração específica do RMI
├── pom.xml                            # Configuração Maven
├── Relatorio_Tecnico_Sistema_Distribuido.md  # Relatório técnico
├──
├── scripts/                           # Scripts de automação
│   ├── iniciar-grupo2.bat               # Inicialização Grupo B (Windows)
│   ├── iniciar-grupo2.ps1               # Inicialização Grupo B (PowerShell)
│   └── parar-grupo2.ps1                 # Parada Grupo B
├──
└── src/main/
    ├── java/br/com/
    │   ├── models/                    # Modelos de Dados
    │   │   ├── Mensagem.java            # Estrutura de mensagens
    │   │   ├── Processo.java            # Modelo de processo
    │   │   ├── TipoMensagem.java        # Enum tipos de mensagem
    │   │   └── ConexaoOutrosProcessos.java # Modelo de conexões
    │   │
    │   ├── grupo1/                   # Grupo A (gRPC + Bully)
    │   │   ├── Processo1.java           # Processo principal do grupo
    │   │   ├── Processo2.java           # Segundo processo
    │   │   ├── Processo3.java           # Terceiro processo
    │   │   └── HeartbeatServiceImpl.java # Implementação heartbeat gRPC
    │   │
    │   ├── grupo2/                   # Grupo B (RMI + Anel)
    │   │   ├── ProcessoRMI1.java        # Primeiro processo RMI
    │   │   ├── ProcessoRMI2.java        # Segundo processo RMI
    │   │   ├── ProcessoRMI3.java        # Terceiro processo RMI
    │   │   ├── ProcessoRMIBase.java     # Classe base RMI
    │   │   └── ProcessoRMIInterface.java # Interface RMI
    │   │
    │   ├── comunicacao/              # Módulos de Comunicação
    │   │   ├── GerenciadorMulticast.java # Comunicação multicast
    │   │   ├── GerenciadorComunicacaoIntergrupos.java # Inter-grupos
    │   │   └── GerenciadorEleicaoSocket.java # Eleição via sockets
    │   │
    │   ├── coordenacao/              # Coordenação Global
    │   │   ├── SupercoordenadorCallbackImpl.java # Callback supercoordenador
    │   │   └── SnapshotManager.java     # Gerenciador de snapshots
    │   │
    │   ├── heartbeat/                # Monitoramento de Vida
    │   │   ├── HeartbeatManager.java    # Gerenciador heartbeat gRPC
    │   │   └── HeartbeatManagerRMI.java # Gerenciador heartbeat RMI
    │   │
    │   └── terminal/                 # Interface de Terminal
    │       └── InterfaceTerminal.java   # Interface principal
    │
    ├── proto/                        # Definições gRPC
    │   └── heartbeat.proto              # Definições de heartbeat
    │
    └── resources/                    # Recursos
        └── (arquivos de configuração)
```

### Arquitetura dos Módulos

#### Models (br.com.models)

- **Mensagem**: Estrutura base para comunicação entre processos
- **Processo**: Modelo de processo com clock de Lamport
- **TipoMensagem**: Enum com tipos (ELEICAO, HEARTBEAT, SNAPSHOT, etc.)
- **ConexaoOutrosProcessos**: Gerenciamento de conexões TCP

#### Grupo 1 (br.com.grupo1) - gRPC + Bully

- **Processo1/2/3**: Implementações específicas de cada processo
- **HeartbeatServiceImpl**: Serviço gRPC para heartbeat
- **Algoritmo Bully**: Integrado nos processos principais

#### Grupo 2 (br.com.grupo2) - RMI + Anel

- **ProcessoRMI1/2/3**: Processos RMI com portas específicas
- **ProcessoRMIBase**: Classe base com funcionalidades comuns
- **ProcessoRMIInterface**: Interface RMI para comunicação remota
- **Algoritmo de Anel**: Implementado via sockets TCP

#### Comunicação (br.com.comunicacao)

- **GerenciadorMulticast**: UDP Multicast para inter-grupos
- **GerenciadorComunicacaoIntergrupos**: Coordenação entre grupos
- **GerenciadorEleicaoSocket**: Eleição via sockets TCP

#### Coordenação (br.com.coordenacao)

- **SupercoordenadorCallbackImpl**: Implementação do supercoordenador
- **SnapshotManager**: Algoritmo Chandy-Lamport

#### Heartbeat (br.com.heartbeat)

- **HeartbeatManager**: Monitoramento gRPC
- **HeartbeatManagerRMI**: Monitoramento RMI

## Requisitos do Sistema

### Portas Utilizadas

#### Grupo A (gRPC + TCP)

- **gRPC**: 8081, 8082, 8083
- **TCP Sockets**: 9001, 9002, 9003

#### Grupo B (RMI + gRPC)

- **RMI Registry**: 1099, 1100, 1101
- **gRPC**: 8091, 8092, 8093

#### Comunicação Global

- **UDP Multicast**: 224.0.0.1:12345
- **Snapshot**: 60001, 60002, 60003, 60011, 60012, 60013

### Verificações Importantes

- Certifique-se de que as portas estão livres
- Firewall configurado para permitir comunicação local
- Java e Maven configurados no PATH

## Compilação e Execução

### 1. Preparação do Ambiente

```bash
# Clone ou navegue até o diretório do projeto
cd sd-simulacao-global-atv6

# Verificar versões
java -version
mvn -version
```

### 2. Compilação Completa

```bash
# Limpeza e compilação
mvn clean compile

# Gerar classes gRPC (Protocol Buffers)
mvn protobuf:compile protobuf:compile-custom

# Compilar tudo e criar JAR
mvn clean package -DskipTests
```

### 3. Execução do Sistema

#### Opção A: Execução via Classes Java

```bash
# Terminal 1 - Processo1 (Grupo A)
java -cp target/classes br.com.grupo1.Processo1

# Terminal 2 - Processo2 (Grupo A)
java -cp target/classes br.com.grupo1.Processo2

# Terminal 3 - Processo3 (Grupo A)
java -cp target/classes br.com.grupo1.Processo3

# Terminal 4 - ProcessoRMI1 (Grupo B)
java -cp target/classes br.com.grupo2.ProcessoRMI1

# Terminal 5 - ProcessoRMI2 (Grupo B)
java -cp target/classes br.com.grupo2.ProcessoRMI2

# Terminal 6 - ProcessoRMI3 (Grupo B)
java -cp target/classes br.com.grupo2.ProcessoRMI3

# Terminal 7 - Interface Principal
java -cp target/classes br.com.terminal.InterfaceTerminal
```

### 4. Verificação da Execução

```bash
# Verificar processos Java em execução
jps -l

# Verificar portas em uso (Windows)
netstat -an | findstr "8081\|8082\|8083\|1099\|1100\|1101"

# Verificar portas em uso (Linux/macOS)
netstat -an | grep -E "8081|8082|8083|1099|1100|1101"
```

O sistema oferece uma **interface interativa completa** via terminal, permitindo controle total sobre todos os aspectos do sistema distribuído.

### Funcionalidades do Grupo A (gRPC + Bully)

#### Menu do Grupo A

```
=== GRUPO A - gRPC + ALGORITMO BULLY ===
1. Enviar Mensagem entre Processos
2. Iniciar Eleição Bully
3. Status do Heartbeat
4. Simular Falha de Processo
5. Capturar Snapshot Local
6. Visualizar Estado dos Processos
7. Reiniciar Processo Específico
8. Histórico de Mensagens
```

**Operações Disponíveis:**

- **Envio de Mensagens**: Comunicação direta entre Processo1, Processo2 e Processo3
- **Eleição Bully**: Iniciação manual de eleições com visualização do processo
- **Monitoramento**: Status em tempo real dos processos e conexões gRPC
- **Simulação de Falhas**: Desconexão controlada para testar tolerância a falhas
- **Snapshot**: Captura do estado local com clock de Lamport

### Funcionalidades do Grupo B (RMI + Anel)

#### Menu do Grupo B

```
=== GRUPO B - RMI + ALGORITMO DE ANEL ===
1. Enviar Mensagem via RMI
2. Iniciar Eleição de Anel
3. Visualizar Topologia do Anel
4. Processamento com Controle de Acesso
5. Status do Heartbeat RMI
6. Estado dos Processos RMI
7. Reconfigurar Anel
8. Log de Operações RMI
```

**Operações Disponíveis:**

- **Comunicação RMI**: Chamadas remotas transparentes entre ProcessoRMI1/2/3
- **Eleição de Anel**: Algoritmo circular com visualização da propagação
- **Topologia**: Mapa visual do anel lógico e conexões ativas
- **Controle de Acesso**: Operações baseadas em níveis de permissão
- **Monitoramento RMI**: Status das conexões e registry RMI

### Supercoordenação Global

```
=== SUPERCOORDENAÇÃO GLOBAL ===
1. Eleger Supercoordenador
2. Iniciar Snapshot Global
3. Coordenar Comunicação Inter-grupos
4. Status da Coordenação
5. Sincronizar Relógios de Lamport
6. Relatório de Estado Global
```

### Monitoramento e Estatísticas

```
=== MONITORAMENTO DO SISTEMA ===
1. Métricas de Performance
2. Status das Conexões
3. Monitoramento de Heartbeat
4. Estatísticas de Mensagens
5. Sincronização de Relógios
6. Status dos Líderes
7. Histórico de Snapshots
8. Logs do Sistema
```

**Métricas Disponíveis:**

- **Latência**: Tempo de resposta por tipo de comunicação
- **Throughput**: Mensagens por segundo
- **Disponibilidade**: Uptime dos processos
- **Eleições**: Frequência e duração das eleições
- **Falhas**: Taxa de falhas e recuperação

## Algoritmos Implementados

### Algoritmo Bully (Grupo A)

**Descrição**: Algoritmo de eleição baseado em dominância por ID, onde o processo com maior identificador assume a liderança.

**Implementação Detalhada**:

```
1. DETECÇÃO DE FALHA:
   - Timeout de heartbeat (5 segundos)
   - Falha na comunicação gRPC
   - Processo não responde a ping

2. INICIAÇÃO DA ELEIÇÃO:
   - Processo detecta falha do líder
   - Envia mensagem ELECTION para processos com ID maior
   - Aguarda resposta por timeout configurável

3. PROCESSO DE ELEIÇÃO:
   - Se recebe OK: para e aguarda mensagem COORDINATOR
   - Se não recebe OK: declara-se líder
   - Envia COORDINATOR para todos os processos

4. FINALIZAÇÃO:
   - Novo líder assume coordenação
   - Atualiza tabela de roteamento
   - Reinicia heartbeat
```

**Características**:

- **Complexidade**: O(n²) mensagens no pior caso
- **Determinístico**: Sempre elege o processo com maior ID ativo
- **Auto-recuperação**: Re-eleição automática em caso de falha
- **Monitoramento**: Logs detalhados de cada etapa

### Algoritmo de Anel (Grupo B)

**Descrição**: Algoritmo de eleição circular onde um token de eleição circula pelo anel lógico dos processos.

**Implementação Detalhada**:

```
1. TOPOLOGIA DO ANEL:
   - ProcessoRMI1 → ProcessoRMI2 → ProcessoRMI3 → ProcessoRMI1
   - Cada processo conhece apenas o próximo
   - Configuração dinâmica em caso de falhas

2. INICIAÇÃO DA ELEIÇÃO:
   - Qualquer processo pode iniciar
   - Cria token com seu próprio ID
   - Envia token para próximo processo no anel

3. PROPAGAÇÃO DO TOKEN:
   - Processo recebe token de eleição
   - Compara ID do token com seu próprio ID
   - Se seu ID > ID do token: substitui ID no token
   - Se seu ID < ID do token: mantém ID do token
   - Repassa token para próximo processo

4. FINALIZAÇÃO:
   - Token retorna ao processo iniciador
   - ID no token indica o novo líder
   - Líder envia mensagem COORDINATOR
```

**Características**:

- **Complexidade**: Exatamente 2n mensagens
- **Eficiência**: Menor overhead de mensagens
- **Tolerância a Falhas**: Reconfiguração automática do anel
- **Rastreabilidade**: Token carrega histórico da eleição

### Algoritmo de Snapshot Global (Chandy-Lamport)

**Descrição**: Algoritmo para captura consistente do estado global de um sistema distribuído, garantindo uma visão coerente mesmo com comunicação assíncrona.

**Implementação Detalhada**:

```
1. INICIAÇÃO DO SNAPSHOT:
   - Supercoordenador inicia o processo
   - Registra seu estado local
   - Envia marcadores para todos os canais de saída
   - Inicia gravação de mensagens nos canais de entrada

2. PROPAGAÇÃO DOS MARCADORES:
   - Processo recebe primeiro marcador de um canal:
     * Registra estado local
     * Marca canal como vazio
     * Envia marcadores para todos os outros canais
     * Inicia gravação nos canais restantes

   - Processo recebe marcador subsequente:
     * Para gravação no canal correspondente
     * Registra mensagens gravadas como estado do canal

3. COLETA DE ESTADOS:
   - Cada processo envia seu estado local + estados dos canais
   - Supercoordenador coleta todos os estados
   - Monta snapshot global consistente
   - Armazena com timestamp de Lamport

4. SINCRONIZAÇÃO TEMPORAL:
   - Relógios de Lamport garantem ordenação causal
   - Cada mensagem carrega timestamp
   - Eventos são ordenados causalmente
```

**Características**:

- **Consistência**: Garante estado globalmente consistente
- **Não-bloqueante**: Não interrompe operações normais
- **Distribuído**: Cada processo participa ativamente
- **Observabilidade**: Permite análise do estado distribuído

### Sincronização Temporal (Relógios de Lamport)

**Implementação**:

```
1. INICIALIZAÇÃO:
   - Cada processo mantém contador local
   - Contador inicia em 0

2. EVENTOS LOCAIS:
   - Incrementa contador antes do evento
   - Associa timestamp ao evento

3. ENVIO DE MENSAGENS:
   - Incrementa contador
   - Inclui timestamp na mensagem

4. RECEBIMENTO DE MENSAGENS:
   - Compara timestamp recebido com local
   - Atualiza para max(local, recebido) + 1
   - Processa mensagem com novo timestamp
```

**Propriedades Garantidas**:

- **Ordenação Causal**: Se A → B, então timestamp(A) < timestamp(B)
- **Consistência**: Eventos causalmente relacionados são ordenados
- **Rastreabilidade**: Histórico completo de eventos

## Logs e Monitoramento

O sistema implementa um **sistema abrangente de logs** com categorização detalhada e monitoramento em tempo real.

### Categorias de Log

| Categoria         | Descrição                   | Exemplos                                   |
| ----------------- | --------------------------- | ------------------------------------------ |
| **ELECTION**      | Processos de eleição        | Início/fim de eleições, mudanças de líder  |
| **HEARTBEAT**     | Monitoramento de saúde      | Pulsos enviados/recebidos, timeouts        |
| **COMMUNICATION** | Comunicação entre processos | Mensagens gRPC/RMI, multicast UDP          |
| **SNAPSHOT**      | Captura de estado           | Início/fim de snapshots, coleta de estados |
| **COORDINATION**  | Coordenação global          | Ações do supercoordenador                  |
| **ERROR**         | Erros e exceções            | Falhas de conexão, timeouts                |
| **SYSTEM**        | Eventos gerais              | Inicialização, shutdown, configuração      |

### Níveis de Log

```
DEBUG   - Informações detalhadas para desenvolvimento
INFO    - Informações gerais de operação
WARN    - Avisos de situações anômalas
ERROR   - Erros que não impedem a operação
FATAL   - Erros críticos que podem parar o sistema
```

### Estrutura de Arquivos de Log

```
logs/
├── sistema.log              # Log principal consolidado
├── grupo_a_grpc.log         # Logs específicos do Grupo A
├── grupo_b_rmi.log          # Logs específicos do Grupo B
├── heartbeat_monitor.log    # Monitoramento de saúde
├── snapshot_global.log      # Snapshots e estados globais
├── eleicoes.log            # Histórico de eleições
├── comunicacao_inter.log   # Comunicação entre grupos
├── autenticacao.log        # Logs de autenticação
└── performance.log         # Métricas de performance
```

### Formato dos Logs

```
[TIMESTAMP] [NIVEL] [CATEGORIA] [PROCESSO] - MENSAGEM

Exemplo:
[2024-01-15 14:30:25.123] [INFO] [ELECTION] [Processo2] - Iniciando eleição Bully
[2024-01-15 14:30:25.456] [DEBUG] [COMMUNICATION] [ProcessoRMI1] - Enviando mensagem via RMI
[2024-01-15 14:30:25.789] [WARN] [HEARTBEAT] [Processo3] - Timeout no heartbeat do líder
```

### Monitoramento em Tempo Real

**Dashboard de Monitoramento:**

- **Status dos Processos**: Verde (ativo), Amarelo (instável), Vermelho (inativo)
- **Métricas de Performance**: Latência, throughput, uso de recursos
- **Estado das Eleições**: Líder atual, última eleição, próxima verificação
- **Heartbeat Monitor**: Frequência de pulsos, falhas detectadas
- **Comunicação Inter-grupos**: Status das conexões multicast
- **Snapshots**: Último snapshot, próximo agendado, histórico

**Alertas Automáticos:**

- **Falha de Processo**: Notificação imediata
- **Timeout de Eleição**: Alerta de demora excessiva
- **Perda de Conexão**: Problemas de rede detectados
- **Falha de Snapshot**: Erro na captura de estado
- **Tentativa de Acesso Negado**: Violação de segurança

### Ferramentas de Análise

**Comandos de Análise:**

```bash
# Filtrar logs por categoria
grep "ELECTION" logs/sistema.log

# Monitorar logs em tempo real
tail -f logs/sistema.log

# Análise de performance
grep "PERFORMANCE" logs/performance.log | tail -100

# Verificar erros recentes
grep "ERROR\|FATAL" logs/sistema.log | tail -50
```

**Relatórios Automáticos:**

- **Relatório Diário**: Resumo de atividades e métricas
- **Análise de Tendências**: Padrões de uso e performance
- **Detecção de Anomalias**: Comportamentos incomuns
- **Auditoria de Segurança**: Tentativas de acesso e violações

## Tolerância a Falhas

O sistema implementa **múltiplos mecanismos robustos** de tolerância a falhas, garantindo alta disponibilidade e recuperação automática.

### Detecção de Falhas

#### Heartbeat Distribuído

```
GRUPO A (gRPC):
- Intervalo: 3 segundos
- Timeout: 5 segundos
- Método: gRPC Health Check
- Retry: 3 tentativas

GRUPO B (RMI):
- Intervalo: 4 segundos
- Timeout: 6 segundos
- Método: RMI Registry Ping
- Retry: 2 tentativas

SUPERCOORDENAÇÃO:
- Intervalo: 2 segundos
- Timeout: 4 segundos
- Método: UDP Multicast Echo
- Retry: 5 tentativas
```

### Mecanismos de Recuperação

#### Recuperação Automática

```
1. DETECÇÃO DE FALHA:
   ├── Heartbeat timeout detectado
   ├── Log da falha registrado
   └── Início do processo de recuperação

2. ISOLAMENTO:
   ├── Processo falhado marcado como inativo
   ├── Remoção das tabelas de roteamento
   └── Notificação para outros processos

3. RE-ELEIÇÃO:
   ├── Algoritmo de eleição iniciado
   ├── Novo líder eleito
   └── Atualização da topologia

4. RECONFIGURAÇÃO:
   ├── Novas rotas estabelecidas
   ├── Heartbeat reiniciado
   └── Sistema operacional novamente
```

#### Estratégias por Componente

**Grupo A (gRPC + Bully):**

- **Detecção**: Health check gRPC com timeout
- **Recuperação**: Re-eleição Bully automática
- **Reconfiguração**: Atualização de stubs gRPC
- **Fallback**: Processo backup assume temporariamente

**Grupo B (RMI + Anel):**

- **Detecção**: Ping RMI registry periódico
- **Recuperação**: Reconfiguração do anel lógico
- **Reconfiguração**: Novo mapeamento de próximo processo
- **Fallback**: Anel se adapta automaticamente

**Supercoordenação:**

- **Detecção**: Multicast echo com múltiplos grupos
- **Recuperação**: Eleição de novo supercoordenador
- **Reconfiguração**: Redistribuição de responsabilidades
- **Fallback**: Modo descentralizado temporário

### Métricas de Disponibilidade

**Indicadores Monitorados:**

- **MTBF** (Mean Time Between Failures): Tempo médio entre falhas
- **MTTR** (Mean Time To Recovery): Tempo médio de recuperação
- **Uptime**: Percentual de disponibilidade
- **Recovery Rate**: Taxa de sucesso na recuperação
- **False Positive Rate**: Taxa de falsos alarmes

**Objetivos de SLA:**

- Disponibilidade: 99.5%
- Tempo de recuperação: < 30 segundos
- Taxa de recuperação automática: > 95%
- Detecção de falhas: < 10 segundos

## Solução de Problemas

### Problemas Comuns e Soluções

#### Erro: "Porta já em uso"

```bash
# Verificar processos usando as portas
netstat -ano | findstr "8081\|8082\|8083\|1099"

# Matar processo específico (Windows)
taskkill /PID <PID> /F

# Matar processo específico (Linux/macOS)
kill -9 <PID>

# Aguardar liberação da porta
sleep 5
```

#### Erro: "Conexão recusada"

```bash
# Verificar se o processo está rodando
jps -l | grep "br.com"

# Verificar conectividade de rede
telnet localhost 8081

# Reiniciar processo específico
# Parar processo atual e reiniciar
```

#### Erro: "RMI Registry não encontrado"

```bash
# Verificar se RMI Registry está rodando
rmiregistry 1099 &

# Verificar objetos registrados
rmic -list rmi://localhost:1099/

# Reiniciar com configuração correta
java -Djava.rmi.server.hostname=localhost ProcessoRMI1
```

#### Timeout em eleições

```bash
# Verificar logs de eleição
grep "ELECTION" logs/sistema.log | tail -20

# Aumentar timeout temporariamente
# Editar configuração ou reiniciar com parâmetros

# Forçar nova eleição
# Usar interface de terminal para iniciar eleição manual
```

### Comandos de Diagnóstico

#### Verificação de Sistema

```bash
# Status geral do sistema
jps -l | grep br.com
netstat -an | grep -E "808[1-3]|109[9-1]|900[1-3]"

# Verificar logs em tempo real
tail -f logs/sistema.log

# Verificar conectividade multicast
ping 224.0.0.1

# Testar portas específicas
telnet localhost 8081
telnet localhost 1099
```

#### Limpeza de Ambiente

```bash
# Parar todos os processos Java do projeto
jps -l | grep br.com | awk '{print $1}' | xargs kill -9

# Limpar registros RMI
rm -rf /tmp/hsperfdata_*

# Limpar logs antigos
rm -f logs/*.log

# Recompilar projeto
mvn clean compile
```

### Checklist de Verificação

**Durante Execução:**

- [ ] Todos os 6 processos principais rodando
- [ ] Interface de terminal responsiva
- [ ] Logs sendo gerados sem erros críticos
- [ ] Heartbeat funcionando entre processos
- [ ] Eleições completando com sucesso

**Em Caso de Problemas:**

- [ ] Verificar logs para identificar causa raiz
- [ ] Confirmar que portas estão disponíveis
- [ ] Testar conectividade de rede local
- [ ] Reiniciar processos em ordem específica
- [ ] Verificar configuração de firewall/antivírus

### Suporte e Contato

**Documentação Adicional:**

- `CONFIGURACAO_ANEL_RMI.md` - Configuração específica do RMI
- `logs/` - Diretório com logs detalhados do sistema

**Recursos de Debug:**

- Logs categorizados por componente
- Interface de terminal com comandos de diagnóstico
- Métricas de performance em tempo real
- Simulação controlada de falhas para testes

## Autenticação e Segurança

Sistema robusto de **autenticação baseado em tokens** com controle granular de acesso e auditoria completa.

## Conclusão

Este **Sistema Distribuído Multigrupo** representa uma implementação completa e robusta de conceitos fundamentais de sistemas distribuídos, demonstrando:

### Principais Conquistas

- **Arquitetura Híbrida**: Combinação eficiente de gRPC/Bully e RMI/Anel
- **Comunicação Multimodal**: TCP, UDP Multicast e sincronização temporal
- **Algoritmos Clássicos**: Implementação fiel dos algoritmos Bully, Anel e Chandy-Lamport
- **Tolerância a Falhas**: Detecção automática e recuperação robusta
- **Segurança Integrada**: Sistema completo de autenticação e auditoria
- **Monitoramento Avançado**: Logs categorizados e métricas em tempo real

### Extensibilidade

A arquitetura modular permite **futuras expansões**:

- Adição de novos grupos com diferentes tecnologias
- Implementação de algoritmos de consenso adicionais
- Integração com sistemas de monitoramento externos
- Expansão do sistema de autenticação
- Implementação de balanceamento de carga
