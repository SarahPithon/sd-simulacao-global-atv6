package br.com.grupo2;

import br.com.utils.GerenciadorMulticast;
import br.com.utils.TerminalColors;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

/**
 * HeartbeatManagerRMI - Gerenciador de heartbeat para processos RMI
 * Adaptado para trabalhar com algoritmo de eleição em anel
 */
public class HeartbeatManagerRMI {
    
    private ProcessoRMIInterface processoLocal;
    private GerenciadorMulticast multicastManager;
    private ScheduledExecutorService scheduler;
    private boolean heartbeatAtivo;
    private List<String> processosConhecidos;
    private static final long INTERVALO_HEARTBEAT = 3000; // 3 segundos
    private static final long TIMEOUT_HEARTBEAT = 10000; // 10 segundos
    
    public HeartbeatManagerRMI(ProcessoRMIInterface processoLocal) {
        this.processoLocal = processoLocal;
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.heartbeatAtivo = false;
        this.processosConhecidos = new ArrayList<>();
        
        // Inicializa o gerenciador multicast para comunicação adicional
        try {
            this.multicastManager = new GerenciadorMulticast(50002); // Porta específica do grupo 2
            this.multicastManager.iniciarEscuta("HeartbeatManagerRMI");
            System.out.println(TerminalColors.GREEN + "[HEARTBEAT-RMI] Gerenciador multicast iniciado" + TerminalColors.RESET);
        } catch (Exception e) {
            System.err.println("Erro ao inicializar multicast: " + e.getMessage());
        }
        
        // Adiciona processos conhecidos
        processosConhecidos.add("rmi://localhost:1101/ProcessoRMI1");
        processosConhecidos.add("rmi://localhost:1102/ProcessoRMI2");
        processosConhecidos.add("rmi://localhost:1103/ProcessoRMI3");
    }
    
    /**
     * Inicia o heartbeat como líder
     */
    public void iniciarHeartbeatComoLider() {
        if (heartbeatAtivo) {
            System.out.println(TerminalColors.YELLOW + "[HEARTBEAT-RMI] Heartbeat já está ativo" + TerminalColors.RESET);
            return;
        }
        
        heartbeatAtivo = true;
        
        try {
            int processoId = processoLocal.getProcessoId();
            System.out.println(TerminalColors.GREEN + "[HEARTBEAT-RMI] Processo" + processoId + 
                              " iniciando heartbeat como líder" + TerminalColors.RESET);
            
            // Envia heartbeat periodicamente para todos os processos
            scheduler.scheduleAtFixedRate(() -> {
                if (heartbeatAtivo) {
                    enviarHeartbeatParaTodos();
                }
            }, 1, INTERVALO_HEARTBEAT, TimeUnit.MILLISECONDS);
            
            // Monitora falhas de processos
            scheduler.scheduleAtFixedRate(() -> {
                if (heartbeatAtivo) {
                    monitorarProcessos();
                }
            }, 5, 5, TimeUnit.SECONDS);
            
        } catch (RemoteException e) {
            System.err.println("Erro ao iniciar heartbeat como líder: " + e.getMessage());
        }
    }
    
    /**
     * Para o heartbeat
     */
    public void pararHeartbeat() {
        if (!heartbeatAtivo) {
            return;
        }
        
        heartbeatAtivo = false;
        
        try {
            int processoId = processoLocal.getProcessoId();
            System.out.println(TerminalColors.YELLOW + "[HEARTBEAT-RMI] Processo" + processoId + 
                              " parando heartbeat" + TerminalColors.RESET);
        } catch (RemoteException e) {
            System.err.println("Erro ao obter ID do processo: " + e.getMessage());
        }
    }
    
    /**
     * Envia heartbeat para todos os processos conhecidos
     */
    private void enviarHeartbeatParaTodos() {
        try {
            int processoId = processoLocal.getProcessoId();
            
            for (String urlProcesso : processosConhecidos) {
                // Não envia heartbeat para si mesmo
                if (urlProcesso.contains("ProcessoRMI" + processoId)) {
                    continue;
                }
                
                // Tenta enviar heartbeat com retry (3 tentativas)
                boolean sucesso = tentarEnviarHeartbeatComRetry(urlProcesso, processoId);
                
                if (!sucesso) {
                    // Após 3 tentativas falharam, notifica falha do processo
                    String nomeProcesso = extrairNomeProcesso(urlProcesso);
                    System.out.println(TerminalColors.RED + "[HEARTBEAT-RMI] Processo " + 
                                      nomeProcesso + " não responde após 3 tentativas - declarando como falho" + TerminalColors.RESET);
                    notificarFalhaProcesso(nomeProcesso);
                }
            }
            
        } catch (RemoteException e) {
            System.err.println("Erro ao enviar heartbeat: " + e.getMessage());
        }
    }
    
    /**
     * Tenta enviar heartbeat com mecanismo de retry (3 tentativas)
     */
    private boolean tentarEnviarHeartbeatComRetry(String urlProcesso, int processoId) {
        int maxTentativas = 3;
        
        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
            try {
                ProcessoRMIInterface processo = (ProcessoRMIInterface) Naming.lookup(urlProcesso);
                processo.receberHeartbeat(processoId);
                
                // Sucesso - processo respondeu
                if (tentativa > 1) {
                    String nomeProcesso = extrairNomeProcesso(urlProcesso);
                    System.out.println(TerminalColors.GREEN + "[HEARTBEAT-RMI] " + nomeProcesso + 
                                      " respondeu na tentativa " + tentativa + TerminalColors.RESET);
                }
                return true;
                
            } catch (Exception e) {
                String nomeProcesso = extrairNomeProcesso(urlProcesso);
                System.out.println(TerminalColors.YELLOW + "[HEARTBEAT-RMI] Tentativa " + tentativa + 
                                  "/" + maxTentativas + " falhou para " + nomeProcesso + ": " + e.getMessage() + TerminalColors.RESET);
                
                // Se não é a última tentativa, aguarda um pouco antes de tentar novamente
                if (tentativa < maxTentativas) {
                    try {
                        Thread.sleep(1000); // Aguarda 1 segundo entre tentativas
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        
        return false; // Todas as tentativas falharam
    }
    
    /**
     * Monitora a saúde dos processos
     */
    private void monitorarProcessos() {
        try {
            int processoId = processoLocal.getProcessoId();
            
            for (String urlProcesso : processosConhecidos) {
                // Não monitora a si mesmo
                if (urlProcesso.contains("ProcessoRMI" + processoId)) {
                    continue;
                }
                
                try {
                    ProcessoRMIInterface processo = (ProcessoRMIInterface) Naming.lookup(urlProcesso);
                    boolean ativo = processo.estaAtivo();
                    
                    if (!ativo) {
                        String nomeProcesso = extrairNomeProcesso(urlProcesso);
                        System.out.println(TerminalColors.RED + "[HEARTBEAT-RMI] Processo inativo detectado: " + 
                                          nomeProcesso + TerminalColors.RESET);
                        notificarFalhaProcesso(nomeProcesso);
                    }
                    
                } catch (Exception e) {
                    // Processo pode ter falido
                    String nomeProcesso = extrairNomeProcesso(urlProcesso);
                    System.out.println(TerminalColors.RED + "[HEARTBEAT-RMI] Possível falha detectada em " + 
                                      nomeProcesso + TerminalColors.RESET);
                }
            }
            
        } catch (RemoteException e) {
            System.err.println("Erro ao monitorar processos: " + e.getMessage());
        }
    }
    
    /**
     * Notifica outros processos sobre falha detectada via multicast
     */
    private void notificarFalhaProcesso(String processoFalhou) {
        try {
            int processoId = processoLocal.getProcessoId();
            
            // Cria mensagem de notificação de falha
            br.com.models.Mensagem mensagemFalha = new br.com.models.Mensagem();
            mensagemFalha.setIdMensagem(java.util.UUID.randomUUID());
            mensagemFalha.setProcessoRemetente(String.valueOf(processoId));
            mensagemFalha.setProcessoDestinatario("TODOS");
            mensagemFalha.setConteudoMensagem("Processo falhou: " + processoFalhou);
            mensagemFalha.setTipoMensagem(br.com.models.TipoMensagem.PROCESSO_FALHOU);
            mensagemFalha.setClockMensagem(0);
            mensagemFalha.setDataMensagem(java.time.LocalDateTime.now());
            
            // Envia via multicast
            if (multicastManager != null) {
                String mensagemTexto = "FALHA_DETECTADA:" + processoFalhou;
                multicastManager.enviarMensagem(mensagemTexto);
                System.out.println(TerminalColors.YELLOW + "[HEARTBEAT-RMI] Notificação de falha enviada via multicast: " + 
                                  processoFalhou + TerminalColors.RESET);
            } else {
                System.err.println("[HEARTBEAT-RMI] Erro: GerenciadorMulticast não disponível para notificar falha");
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao notificar falha via multicast: " + e.getMessage());
        }
    }
    
    /**
     * Verifica se um processo específico está ativo
     */
    public boolean verificarProcessoAtivo(String urlProcesso) {
        try {
            ProcessoRMIInterface processo = (ProcessoRMIInterface) Naming.lookup(urlProcesso);
            return processo.estaAtivo();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Obtém a lista de processos ativos
     */
    public List<String> obterProcessosAtivos() {
        List<String> processosAtivos = new ArrayList<>();
        
        for (String urlProcesso : processosConhecidos) {
            if (verificarProcessoAtivo(urlProcesso)) {
                processosAtivos.add(extrairNomeProcesso(urlProcesso));
            }
        }
        
        return processosAtivos;
    }
    
    /**
     * Extrai o nome do processo da URL RMI
     */
    private String extrairNomeProcesso(String urlProcesso) {
        String[] partes = urlProcesso.split("/");
        return partes[partes.length - 1];
    }
    
    /**
     * Extrai o ID do processo da URL RMI
     */
    private int extrairIdProcesso(String urlProcesso) {
        String nomeProcesso = extrairNomeProcesso(urlProcesso);
        // Extrai o número do nome (ex: ProcessoRMI1 -> 1)
        return Integer.parseInt(nomeProcesso.replaceAll("\\D+", ""));
    }
    
    /**
     * Obtém o gerenciador multicast
     */
    public GerenciadorMulticast getMulticastManager() {
        return multicastManager;
    }
    
    /**
     * Verifica se o heartbeat está ativo
     */
    public boolean isHeartbeatAtivo() {
        return heartbeatAtivo;
    }
    
    /**
     * Finaliza o HeartbeatManager
     */
    public void finalizar() {
        pararHeartbeat();
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (multicastManager != null) {
            // Finaliza o multicast se necessário
            System.out.println(TerminalColors.YELLOW + "[HEARTBEAT-RMI] HeartbeatManager finalizado" + TerminalColors.RESET);
        }
    }
}