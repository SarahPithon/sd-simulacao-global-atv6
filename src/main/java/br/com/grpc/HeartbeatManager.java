package br.com.grpc;

import br.com.models.ConexaoOutrosProcessos;
import br.com.utils.GerenciadorMulticast;
import br.com.utils.TerminalColors;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;


public class HeartbeatManager {
    
    private final String processName;
    private final int grpcPort;
    private final List<ConexaoOutrosProcessos> processos;
    private Server server;
    private ScheduledExecutorService scheduler;
    private boolean isLeader;
    private boolean serverStarted;
    private GerenciadorMulticast multicastManager;
    private List<HeartbeatClient> heartbeatClients;
    
    private static final long HEARTBEAT_INTERVAL = 5000;
    private static final long HEARTBEAT_TIMEOUT = 15000;
    
    public HeartbeatManager(String processName, int grpcPort, List<ConexaoOutrosProcessos> processos) {
        this.processName = processName;
        this.grpcPort = grpcPort;
        this.processos = processos;
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.isLeader = false;
        this.serverStarted = false;
        this.heartbeatClients = new ArrayList<>();
        

        this.multicastManager = null;
    }

    public void setMulticastManager(GerenciadorMulticast multicastManager) {
        this.multicastManager = multicastManager;
        System.out.println(TerminalColors.successMessage("[HEARTBEAT] Gerenciador multicast configurado para " + processName));
    }
    
    public void startServer() throws IOException {
        if (serverStarted) {
            return;
        }
        
        server = ServerBuilder.forPort(grpcPort)
                .addService(new HeartbeatServiceImpl(processName))
                .build()
                .start();
        
        serverStarted = true;
        System.out.println(TerminalColors.successMessage("[HEARTBEAT] Servidor gRPC iniciado na porta " + grpcPort + " para " + processName));
        

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(TerminalColors.warningMessage("[HEARTBEAT] Parando servidor gRPC..."));
            HeartbeatManager.this.stop();
        }));
    }
    
    public void startAsLeader() {
        if (isLeader) {
            return;
        }
        
        isLeader = true;
        System.out.println(TerminalColors.successMessage("[HEARTBEAT] " + processName + " iniciado como líder"));
        
        initializeHeartbeatClients();
        
        scheduler.scheduleAtFixedRate(() -> {
            if (isLeader) {
                sendHeartbeatToAll();
            }
        }, 2, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    private void initializeHeartbeatClients() {
        heartbeatClients.clear();
        
        for (ConexaoOutrosProcessos processo : processos) {
            try {
                int targetPort = getGrpcPortForProcess(processo.getNomeProcesso());
                HeartbeatClient client = new HeartbeatClient("localhost", targetPort, 
                                                           processo.getNomeProcesso(), processName);
                heartbeatClients.add(client);
                System.out.println(TerminalColors.successMessage("[HEARTBEAT] Cliente criado para " + processo.getNomeProcesso() + " na porta " + targetPort));
            } catch (Exception e) {
                System.err.println(TerminalColors.errorMessage("[HEARTBEAT] Erro ao criar cliente para " + processo.getNomeProcesso() + ": " + e.getMessage()));
            }
        }
    }
    
    private void sendHeartbeatToAll() {
        int processosAtivos = 0;
        int totalProcessos = heartbeatClients.size();
        StringBuilder statusProcessos = new StringBuilder();
        
        for (HeartbeatClient client : heartbeatClients) {
            try {
                boolean success = client.sendPing();
                if (success) {
                    processosAtivos++;
                    statusProcessos.append(client.getTargetProcess()).append(":OK ");
                } else {
                    statusProcessos.append(client.getTargetProcess()).append(":FALHA ");
                    System.out.println(TerminalColors.warningMessage("[HEARTBEAT] Falha ao enviar heartbeat para " + client.getTargetProcess()));
                }
            } catch (Exception e) {
                statusProcessos.append(client.getTargetProcess()).append(":ERRO ");
                System.err.println(TerminalColors.errorMessage("[HEARTBEAT] Erro ao enviar heartbeat: " + e.getMessage()));
            }
        }
        
        if (multicastManager != null) {
            try {
                if (processosAtivos == totalProcessos) {

                    String mensagemOK = "HEARTBEAT_OK:" + processName + ":Todos os processos ativos (" + processosAtivos + "/" + totalProcessos + ") - " + statusProcessos.toString().trim();
                    multicastManager.enviarMensagem(mensagemOK);
                    System.out.println(TerminalColors.successMessage("[HEARTBEAT] Status enviado via multicast: Todos os processos ativos"));
                } else {
                    String mensagemAlert = "HEARTBEAT_ALERT:" + processName + ":Alguns processos inativos (" + processosAtivos + "/" + totalProcessos + ") - " + statusProcessos.toString().trim();
                    multicastManager.enviarMensagem(mensagemAlert);
                    System.out.println(TerminalColors.warningMessage("[HEARTBEAT] Alerta enviado via multicast: Alguns processos inativos (" + processosAtivos + "/" + totalProcessos + ")"));
                }
            } catch (Exception e) {
                System.err.println(TerminalColors.errorMessage("[HEARTBEAT] Erro ao enviar status via multicast: " + e.getMessage()));
            }
        }
    }
    
    private int getGrpcPortForProcess(String processName) {
        switch (processName) {
            case "Processo1":
                return 9011;
            case "Processo2":
                return 9012;
            case "Processo3":
                return 9013;
            default:
                return 9010 + Integer.parseInt(processName.replaceAll("\\D", ""));
        }
    }
    
    public void stop() {
        isLeader = false;
        

        for (HeartbeatClient client : heartbeatClients) {
            try {
                client.shutdown();
            } catch (Exception e) {
                System.err.println(TerminalColors.errorMessage("[HEARTBEAT] Erro ao parar cliente: " + e.getMessage()));
            }
        }
        heartbeatClients.clear();
        

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
        

        if (server != null && !server.isShutdown()) {
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        serverStarted = false;
        System.out.println(TerminalColors.warningMessage("[HEARTBEAT] HeartbeatManager parado para " + processName));
    }
    
    public boolean isLeader() {
        return isLeader;
    }
    
    public GerenciadorMulticast getMulticastManager() {
        return multicastManager;
    }
    
    public boolean isServerStarted() {
        return serverStarted;
    }
}