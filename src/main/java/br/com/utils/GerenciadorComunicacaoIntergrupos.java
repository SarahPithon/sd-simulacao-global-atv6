package br.com.utils;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Gerenciador de comunicação entre grupos via sockets
 * Responsável por coordenar a comunicação entre processos de diferentes grupos
 * para determinar e gerenciar o supercoordenador global
 */
public class GerenciadorComunicacaoIntergrupos {
    
    private static final int PORTA_GRUPO1 = 9001;
    private static final int PORTA_GRUPO2 = 9002;
    private static final String HOST = "localhost";
    
    private final int grupoAtual;
    private final String processoId;
    private ServerSocket serverSocket;
    private boolean ativo = false;
    private ExecutorService executorService;
    private SupercoordenadorCallback callback;
    
    // Mapa para armazenar informações dos grupos
    private Map<Integer, String> lideresPorGrupo = new ConcurrentHashMap<>();
    private Map<Integer, List<String>> processosPorGrupo = new ConcurrentHashMap<>();
    
    public GerenciadorComunicacaoIntergrupos(int grupoAtual, String processoId, SupercoordenadorCallback callback) {
        this.grupoAtual = grupoAtual;
        this.processoId = processoId;
        this.callback = callback;
        this.executorService = Executors.newCachedThreadPool();
    }
    
    /**
     * Inicia o servidor de comunicação intergrupos
     */
    public void iniciar() {
        try {
            int porta = (grupoAtual == 1) ? PORTA_GRUPO1 : PORTA_GRUPO2;
            serverSocket = new ServerSocket(porta);
            ativo = true;
            
            System.out.println("[" + processoId + "] Servidor intergrupos iniciado na porta " + porta);
            
            // Thread para aceitar conexões
            executorService.submit(() -> {
                while (ativo && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        executorService.submit(() -> processarConexao(clientSocket));
                    } catch (IOException e) {
                        if (ativo) {
                            System.err.println("[" + processoId + "] Erro ao aceitar conexão: " + e.getMessage());
                        }
                    }
                }
            });
            
        } catch (IOException e) {
            System.err.println("[" + processoId + "] Erro ao iniciar servidor intergrupos: " + e.getMessage());
        }
    }
    
    /**
     * Processa uma conexão de cliente
     */
    private void processarConexao(Socket clientSocket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            String mensagem = reader.readLine();
            if (mensagem != null) {
                String resposta = processarMensagem(mensagem);
                writer.println(resposta);
            }
            
        } catch (IOException e) {
            System.err.println("[" + processoId + "] Erro ao processar conexão: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignorar erro ao fechar socket
            }
        }
    }
    
    /**
     * Processa mensagens recebidas de outros grupos
     */
    private String processarMensagem(String mensagem) {
        try {
            String[] partes = mensagem.split(":", 3);
            String comando = partes[0];
            
            switch (comando) {
                case "STATUS_GRUPO":
                    return callback.obterStatusGrupo();
                    
                case "NOVO_LIDER":
                    int grupo = Integer.parseInt(partes[1]);
                    String novoLider = partes[2];
                    lideresPorGrupo.put(grupo, novoLider);
                    verificarSupercoordenador();
                    return "OK";
                    
                case "SOLICITAR_PRINT_GERAL":
                    return obterPrintGeral();
                    
                case "PING":
                    return "PONG:" + grupoAtual + ":" + processoId;
                    
                default:
                    return "COMANDO_DESCONHECIDO";
            }
        } catch (Exception e) {
            System.err.println("[" + processoId + "] Erro ao processar mensagem: " + e.getMessage());
            return "ERRO:" + e.getMessage();
        }
    }
    
    /**
     * Envia mensagem para outro grupo
     */
    public String enviarMensagem(int grupoDestino, String mensagem) {
        int porta = (grupoDestino == 1) ? PORTA_GRUPO1 : PORTA_GRUPO2;
        
        try (Socket socket = new Socket(HOST, porta);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            writer.println(mensagem);
            return reader.readLine();
            
        } catch (IOException e) {
            System.err.println("[" + processoId + "] Erro ao enviar mensagem para grupo " + grupoDestino + ": " + e.getMessage());
            return "ERRO_CONEXAO";
        }
    }
    
    /**
     * Notifica outros grupos sobre mudança de liderança
     */
    public void notificarNovoLider(String novoLider) {
        int grupoDestino = (grupoAtual == 1) ? 2 : 1;
        String mensagem = "NOVO_LIDER:" + grupoAtual + ":" + novoLider;
        
        executorService.submit(() -> {
            String resposta = enviarMensagem(grupoDestino, mensagem);
            System.out.println("[" + processoId + "] Notificação de novo líder enviada para grupo " + grupoDestino + ": " + resposta);
        });
    }
    
    /**
     * Verifica e define o supercoordenador baseado nos líderes dos grupos
     */
    private void verificarSupercoordenador() {
        try {
            // Lógica para determinar supercoordenador
            // Por exemplo: grupo com menor ID que tem líder ativo
            if (callback.isLiderGrupo()) {
                boolean deveSerSupercoordenador = true;
                
                // Verifica se há líder no grupo 1 (prioridade)
                if (grupoAtual == 2 && lideresPorGrupo.containsKey(1)) {
                    deveSerSupercoordenador = false;
                }
                
                if (deveSerSupercoordenador) {
                    callback.definirNovoSupercoordenador(grupoAtual, processoId);
                    System.out.println("[" + processoId + "] Definido como SUPERCOORDENADOR");
                }
            }
        } catch (Exception e) {
            System.err.println("[" + processoId + "] Erro ao verificar supercoordenador: " + e.getMessage());
        }
    }
    
    /**
     * Obtém print geral de todos os grupos
     */
    public String obterPrintGeral() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== PRINT GERAL DO SISTEMA ===\n");
        sb.append("Supercoordenador: Grupo ").append(grupoAtual).append(" - ").append(processoId).append("\n");
        sb.append("\n--- STATUS DOS GRUPOS ---\n");
        
        try {
            // Status do grupo atual
            sb.append("Grupo ").append(grupoAtual).append(": ").append(callback.obterStatusGrupo()).append("\n");
            
            // Status do outro grupo
            int outroGrupo = (grupoAtual == 1) ? 2 : 1;
            String statusOutroGrupo = enviarMensagem(outroGrupo, "STATUS_GRUPO");
            if (!"ERRO_CONEXAO".equals(statusOutroGrupo)) {
                sb.append("Grupo ").append(outroGrupo).append(": ").append(statusOutroGrupo).append("\n");
            } else {
                sb.append("Grupo ").append(outroGrupo).append(": DESCONECTADO\n");
            }
            
        } catch (Exception e) {
            sb.append("Erro ao obter status: ").append(e.getMessage()).append("\n");
        }
        
        sb.append("\n==============================\n");
        return sb.toString();
    }
    
    /**
     * Solicita print geral como supercoordenador
     */
    public void solicitarPrintGeral() {
        if (callback != null) {
            try {
                if (callback.isLiderGrupo()) {
                    String printGeral = obterPrintGeral();
                    System.out.println(printGeral);
                }
            } catch (Exception e) {
                System.err.println("[" + processoId + "] Erro ao solicitar print geral: " + e.getMessage());
            }
        }
    }
    
    /**
     * Finaliza o gerenciador
     */
    public void finalizar() {
        ativo = false;
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("[" + processoId + "] Erro ao fechar servidor: " + e.getMessage());
            }
        }
        
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        
        System.out.println("[" + processoId + "] Gerenciador de comunicação intergrupos finalizado");
    }
}