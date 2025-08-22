# Configuração do Anel RMI - Grupo 2

## Problema Identificado

O problema das mensagens excessivas de "Timeout de heartbeat detectado" estava ocorrendo porque:

1. **Os processos RMI não estavam rodando** - O sistema de heartbeat tentava se comunicar com processos que não existiam
2. **O anel não estava configurado** - Cada processo precisa saber qual é o próximo processo no anel
3. **Spam de mensagens** - O sistema mostrava timeout a cada 2 segundos

## Solução Implementada

### 1. Inicialização dos Processos

Para iniciar todos os processos RMI corretamente:

```bash
# Execute o script de inicialização
.\scripts\iniciar-grupo2.bat
```

Este script:
- Inicia o RMI Registry na porta 1099
- Compila o projeto
- Inicia ProcessoRMI1 na porta 1101
- Inicia ProcessoRMI2 na porta 1102  
- Inicia ProcessoRMI3 na porta 1103

### 2. Configuração do Anel

Após iniciar os processos, você deve configurar o anel em cada processo:

**ProcessoRMI1:**
- Menu → Opção 6 (Configurar próximo processo)
- Escolher ProcessoRMI2

**ProcessoRMI2:**
- Menu → Opção 6 (Configurar próximo processo)
- Escolher ProcessoRMI3

**ProcessoRMI3:**
- Menu → Opção 6 (Configurar próximo processo)
- Escolher ProcessoRMI1

### 3. Melhorias no Sistema de Heartbeat

O código foi atualizado para:

- **Verificar se o próximo processo está configurado** antes de enviar heartbeat
- **Reduzir spam de mensagens** - timeout só é mostrado a cada 30 segundos
- **Mensagem mais informativa** - indica que o próximo processo pode estar inativo

## Verificação do Sistema

### Verificar se os processos estão rodando:

```bash
# Verificar portas ativas
netstat -an | findstr :110

# Verificar processos Java
jps
```

### Resultado esperado:

```
TCP    0.0.0.0:1101           0.0.0.0:0              LISTENING
TCP    0.0.0.0:1102           0.0.0.0:0              LISTENING  
TCP    0.0.0.0:1103           0.0.0.0:0              LISTENING
```

## Fluxo de Configuração Completo

1. **Executar script de inicialização**
   ```bash
   .\scripts\iniciar-grupo2.bat
   ```

2. **Aguardar todos os processos iniciarem** (cerca de 10-15 segundos)

3. **Configurar o anel em cada processo:**
   - ProcessoRMI1 → próximo: ProcessoRMI2
   - ProcessoRMI2 → próximo: ProcessoRMI3  
   - ProcessoRMI3 → próximo: ProcessoRMI1

4. **Testar eleição em anel** em qualquer processo (opção 4 do menu)

5. **Verificar comunicação RMI** entre os processos

## Troubleshooting

### Se ainda aparecer timeout:

1. Verifique se todos os 3 processos estão rodando
2. Confirme se o anel foi configurado corretamente
3. Teste a conectividade RMI entre os processos
4. Reinicie os processos se necessário

### Para parar os processos:

```bash
.\scripts\parar-grupo2.ps1
```

## Arquitetura do Sistema

```
ProcessoRMI1 (porta 1101) → ProcessoRMI2 (porta 1102) → ProcessoRMI3 (porta 1103) → ProcessoRMI1
     ↑                                                                                    ↓
     └────────────────────────────── ANEL ──────────────────────────────────────────────┘
```

Cada processo:
- Envia heartbeat para o próximo processo a cada 3 segundos
- Monitora timeout de heartbeat (5 segundos)
- Inicia eleição se detectar falha do líder
- Usa algoritmo de eleição em anel com parâmetros (10, 20, 30)