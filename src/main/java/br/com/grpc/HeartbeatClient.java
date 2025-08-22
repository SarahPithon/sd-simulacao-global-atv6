package br.com.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import br.com.utils.TerminalColors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class HeartbeatClient {
    
    private final ManagedChannel channel;
    private final HeartbeatServiceGrpc.HeartbeatServiceBlockingStub blockingStub;
    private final String targetProcess;
    private final String senderProcess;
    private final AtomicInteger sequenceNumber;
    
    public HeartbeatClient(String host, int port, String targetProcess, String senderProcess) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = HeartbeatServiceGrpc.newBlockingStub(channel);
        this.targetProcess = targetProcess;
        this.senderProcess = senderProcess;
        this.sequenceNumber = new AtomicInteger(0);
    }
    
    public boolean sendPing() {
        return sendPing(5000);
    }
    
    public boolean sendPing(long timeoutMs) {
        try {
            int currentSeq = sequenceNumber.incrementAndGet();
            
            PingRequest request = PingRequest.newBuilder()
                    .setSenderProcess(senderProcess)
                    .setTimestamp(System.currentTimeMillis())
                    .setSequenceNumber(currentSeq)
                    .build();
            
            System.out.println(TerminalColors.successMessage(
                "[HEARTBEAT] Sinal de vida enviado para " + targetProcess));
            

            HeartbeatServiceGrpc.HeartbeatServiceBlockingStub stubWithTimeout = 
                blockingStub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS);
            
            PongResponse response = stubWithTimeout.ping(request);
            
            if (response.getSuccess() && response.getSequenceNumber() == currentSeq) {
                System.out.println(TerminalColors.successMessage(
                    "[HEARTBEAT] Pong recebido de " + response.getResponderProcess() + 
                    " (seq: " + response.getSequenceNumber() + ")"));
                return true;
            } else {
                System.out.println(TerminalColors.warningMessage(
                    "[HEARTBEAT] Pong inválido de " + response.getResponderProcess() + 
                    " (success: " + response.getSuccess() + ", seq: " + response.getSequenceNumber() + ")"));
                return false;
            }
            
        } catch (StatusRuntimeException e) {

            return false;
        } catch (Exception e) {

            return false;
        }
    }
    
    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(TerminalColors.errorMessage(
                "[HEARTBEAT] Erro ao encerrar conexão com " + targetProcess));
        }
    }
    
    public boolean isChannelActive() {
        return !channel.isShutdown() && !channel.isTerminated();
    }
    
    public String getTargetProcess() {
        return targetProcess;
    }
    
    public String getSenderProcess() {
        return senderProcess;
    }
}