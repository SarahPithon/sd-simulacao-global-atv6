package br.com.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;


public class ClienteHeartbeat {
    
    private final ManagedChannel channel;
    private final String targetHost;
    private final int targetPort;
    
    public ClienteHeartbeat(String host, int port) {
        this.targetHost = host;
        this.targetPort = port;
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }
    
    public boolean enviarPing(String remetente, String destinatario) {
        try {

            return verificarConectividade();
        } catch (Exception e) {
            System.err.println("[HEARTBEAT] Erro ao enviar ping para " + destinatario + ": " + e.getMessage());
            return false;
        }
    }
    
    private boolean verificarConectividade() {
        try {

            return !channel.isShutdown() && !channel.isTerminated();
        } catch (Exception e) {
            return false;
        }
    }
    
    public void encerrar() throws InterruptedException {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    
    public boolean isAtivo() {
        return channel != null && !channel.isShutdown() && !channel.isTerminated();
    }
}