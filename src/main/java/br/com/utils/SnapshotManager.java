package br.com.utils;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gerenciador de snapshot distribuído via sockets
 * Permite coleta de informações de estado de todos os processos do sistema
 */
public class SnapshotManager {
    
    // Porta base para comunicação de snapshot (cada processo usa porta base + offset)
    private static final int SNAPSHOT_PORT_BASE = 60000;
    
    // Protocolo de mensagens
    public static final String SNAPSHOT_REQUEST = "SNAPSHOT_REQUEST";
    public static final String SNAPSHOT_RESPONSE = "SNAPSHOT_RESPONSE";
    
    // Mapeamento de processos e suas portas de snapshot
    private static final ConcurrentHashMap<String, Integer> PROCESS_SNAPSHOT_PORTS = new ConcurrentHashMap<>();
    
    static {
        // Grupo 1 - Processos normais
        PROCESS_SNAPSHOT_PORTS.put("Processo1", SNAPSHOT_PORT_BASE + 1); // 60001
        PROCESS_SNAPSHOT_PORTS.put("Processo2", SNAPSHOT_PORT_BASE + 2); // 60002
        PROCESS_SNAPSHOT_PORTS.put("Processo3", SNAPSHOT_PORT_BASE + 3); // 60003
        
        // Grupo 2 - Processos RMI
        PROCESS_SNAPSHOT_PORTS.put("ProcessoRMI1", SNAPSHOT_PORT_BASE + 11); // 60011
        PROCESS_SNAPSHOT_PORTS.put("ProcessoRMI2", SNAPSHOT_PORT_BASE + 12); // 60012
        PROCESS_SNAPSHOT_PORTS.put("ProcessoRMI3", SNAPSHOT_PORT_BASE + 13); // 60013
    }
    
    private final String processName;
    private final int snapshotPort;
    private ServerSocket snapshotServer;
    private ExecutorService executor;
    private volatile boolean isRunning = false;
    
    // Interface para callback de snapshot
    public interface SnapshotCallback {
        String getSnapshotInfo();
    }
    
    private SnapshotCallback snapshotCallback;
    
    public SnapshotManager(String processName, SnapshotCallback callback) {
        this.processName = processName;
        this.snapshotCallback = callback;
        this.snapshotPort = PROCESS_SNAPSHOT_PORTS.getOrDefault(processName, SNAPSHOT_PORT_BASE);
        this.executor = Executors.newCachedThreadPool();
    }
    
    /**
     * Inicia o servidor de snapshot para escutar pedidos
     */
    public void startSnapshotServer() {
        if (isRunning) {
            return;
        }
        
        executor.submit(() -> {
            try {
                snapshotServer = new ServerSocket(snapshotPort);
                isRunning = true;
                System.out.println(TerminalColors.successMessage(
                    "[SNAPSHOT] Servidor de snapshot iniciado para " + processName + " na porta " + snapshotPort));
                
                while (isRunning && !snapshotServer.isClosed()) {
                    try {
                        Socket clientSocket = snapshotServer.accept();
                        executor.submit(() -> handleSnapshotRequest(clientSocket));
                    } catch (IOException e) {
                        if (isRunning) {
                            System.err.println("[SNAPSHOT] Erro ao aceitar conexão: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("[SNAPSHOT] Erro ao iniciar servidor de snapshot: " + e.getMessage());
            }
        });
    }
    
    /**
     * Processa pedido de snapshot recebido
     */
    private void handleSnapshotRequest(Socket clientSocket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            String request = reader.readLine();
            if (SNAPSHOT_REQUEST.equals(request)) {
                String snapshotInfo = snapshotCallback.getSnapshotInfo();
                writer.println(SNAPSHOT_RESPONSE + ":" + snapshotInfo);
                System.out.println(TerminalColors.warningMessage(
                    "[SNAPSHOT] Respondido pedido de snapshot: " + snapshotInfo));
            }
        } catch (IOException e) {
            System.err.println("[SNAPSHOT] Erro ao processar pedido: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignora erro ao fechar socket
            }
        }
    }
    
    /**
     * Coleta snapshot de um processo específico
     */
    public static String collectSnapshotFromProcess(String targetProcessName) {
        Integer port = PROCESS_SNAPSHOT_PORTS.get(targetProcessName);
        if (port == null) {
            return targetProcessName + ": PROCESSO NÃO ENCONTRADO";
        }
        
        try (Socket socket = new Socket("localhost", port);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            // Envia pedido de snapshot
            writer.println(SNAPSHOT_REQUEST);
            
            // Lê resposta
            String response = reader.readLine();
            if (response != null && response.startsWith(SNAPSHOT_RESPONSE + ":")) {
                return response.substring((SNAPSHOT_RESPONSE + ":").length());
            } else {
                return targetProcessName + ": RESPOSTA INVÁLIDA";
            }
            
        } catch (IOException e) {
            return targetProcessName + ": INDISPONÍVEL (" + e.getMessage() + ")";
        }
    }
    
    /**
     * Coleta snapshot de todos os processos do sistema
     */
    public static String collectSystemSnapshot() {
        StringBuilder snapshot = new StringBuilder();
        snapshot.append("\n").append(TerminalColors.successMessage("=== SNAPSHOT DO SISTEMA DISTRIBUÍDO ===")).append("\n");
        snapshot.append(TerminalColors.warningMessage("Coletado por: Processo3 (SUPERCOORDENADOR)")).append("\n\n");
        
        // Coleta informações do Grupo 1
        snapshot.append(TerminalColors.multicastMessage("--- GRUPO 1 (Processos Normais) ---")).append("\n");
        for (String processName : new String[]{"Processo1", "Processo2", "Processo3"}) {
            String info = collectSnapshotFromProcess(processName);
            snapshot.append("• ").append(info).append("\n");
        }
        
        snapshot.append("\n");
        
        // Coleta informações do Grupo 2
        snapshot.append(TerminalColors.multicastMessage("--- GRUPO 2 (Processos RMI) ---")).append("\n");
        for (String processName : new String[]{"ProcessoRMI1", "ProcessoRMI2", "ProcessoRMI3"}) {
            String info = collectSnapshotFromProcess(processName);
            snapshot.append("• ").append(info).append("\n");
        }
        
        snapshot.append("\n").append(TerminalColors.successMessage("=== FIM DO SNAPSHOT ==="));
        return snapshot.toString();
    }
    
    /**
     * Para o servidor de snapshot
     */
    public void stopSnapshotServer() {
        isRunning = false;
        if (snapshotServer != null && !snapshotServer.isClosed()) {
            try {
                snapshotServer.close();
            } catch (IOException e) {
                System.err.println("[SNAPSHOT] Erro ao fechar servidor: " + e.getMessage());
            }
        }
        if (executor != null) {
            executor.shutdown();
        }
    }
    
    /**
     * Obtém a porta de snapshot para um processo
     */
    public static int getSnapshotPort(String processName) {
        return PROCESS_SNAPSHOT_PORTS.getOrDefault(processName, SNAPSHOT_PORT_BASE);
    }
}