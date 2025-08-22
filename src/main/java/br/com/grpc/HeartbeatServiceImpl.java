package br.com.grpc;

import io.grpc.stub.StreamObserver;
import br.com.utils.TerminalColors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class HeartbeatServiceImpl extends HeartbeatServiceGrpc.HeartbeatServiceImplBase {
    
    private final String nomeProcesso;
    private volatile boolean ativo = true;
    
    public HeartbeatServiceImpl(String nomeProcesso) {
        this.nomeProcesso = nomeProcesso;
    }
    
    @Override
    public void ping(PingRequest request, StreamObserver<PongResponse> responseObserver) {
        try {

            PongResponse response = PongResponse.newBuilder()
                    .setResponderProcess(nomeProcesso)
                    .setTimestamp(System.currentTimeMillis())
                    .setSequenceNumber(request.getSequenceNumber())
                    .setSuccess(ativo)
                    .build();
            

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {

            PongResponse errorResponse = PongResponse.newBuilder()
                    .setResponderProcess(nomeProcesso)
                    .setTimestamp(System.currentTimeMillis())
                    .setSequenceNumber(request.getSequenceNumber())
                    .setSuccess(false)
                    .build();
            
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }
    
    public boolean responderPing(String remetente) {
        return ativo;
    }
    
    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
        if (!ativo) {
            System.out.println(TerminalColors.warningMessage(
                "[HEARTBEAT] Processo " + nomeProcesso + " marcado como inativo"));
        }
    }
    
    public boolean isAtivo() {
        return ativo;
    }
    
    public String getNomeProcesso() {
        return nomeProcesso;
    }
    
    public void simularRequisicaoGrpc(String requisicao, String resposta) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println(String.format("[%s] [gRPC] Processando: %s -> %s", timestamp, requisicao, resposta));
    }
}