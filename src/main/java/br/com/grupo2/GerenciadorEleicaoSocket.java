package br.com.grupo2;

import br.com.utils.TerminalColors;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gerenciador de eleição em anel usando sockets TCP
 * Implementa o algoritmo de eleição em anel onde:
 * - O processo que detecta falha inicia a eleição
 * - Mensagem circula pelo anel com parâmetros de eleição
 * - Quando volta ao iniciador, ele determina o líder
 */
public class GerenciadorEleicaoSocket {
    
    private final int processoId;
    private final int parametroEleicao;
    private final int portaSocket;
    private boolean iniciouEleicao = false;
    private int maiorParametroRecebido = 0;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private ProcessoRMIBase processoBase;
    
    // Portas para comunicação de eleição via socket
    private static final int PORTA_BASE_ELEICAO = 2100;
    
    // Lista de todos os processos no anel (em ordem)
    private static final int[] PROCESSOS_ANEL = {1, 2, 3};
    private static final int TIMEOUT_CONEXAO = 3000; // 3 segundos
    private static final int MAX_TENTATIVAS = 2; // Máximo 2 tentativas por processo
    
    public GerenciadorEleicaoSocket(int processoId, int parametroEleicao, ProcessoRMIBase processoBase) {
        this.processoId = processoId;
        this.parametroEleicao = parametroEleicao;
        this.portaSocket = PORTA_BASE_ELEICAO + processoId;
        this.executor = Executors.newFixedThreadPool(2);
        this.processoBase = processoBase;
        
        iniciarServidorSocket();
    }
    
    /**
     * Inicia o servidor socket para receber mensagens de eleição
     */
    private void iniciarServidorSocket() {
        executor.submit(() -> {
            try {
                serverSocket = new ServerSocket(portaSocket);
                System.out.println(TerminalColors.CYAN + "[ELEIÇÃO-SOCKET] Processo " + processoId + 
                                 " escutando eleições na porta " + portaSocket + TerminalColors.RESET);
                
                while (!serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        executor.submit(() -> processarMensagemEleicao(clientSocket));
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            System.err.println("Erro ao aceitar conexão de eleição: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao iniciar servidor de eleição: " + e.getMessage());
            }
        });
    }
    
    /**
     * Inicia uma nova eleição em anel
     */
    public void iniciarEleicao() {
        if (iniciouEleicao) {
            System.out.println(TerminalColors.YELLOW + "[ELEIÇÃO-SOCKET] Processo " + processoId + 
                             " já iniciou uma eleição" + TerminalColors.RESET);
            return;
        }
        
        // Verifica se o líder atual ainda está ativo antes de iniciar eleição
        try {
            int liderAtual = processoBase.getLiderAtual();
            if (liderAtual != -1 && liderAtual != processoId && verificarSeProcessoEstaAtivo(liderAtual)) {
                System.out.println(TerminalColors.YELLOW + "[ELEIÇÃO-SOCKET] Líder atual (Processo " + liderAtual + 
                                 ") ainda está ativo, não iniciando eleição" + TerminalColors.RESET);
                return;
            }
        } catch (java.rmi.RemoteException e) {
            System.err.println("Erro ao verificar líder atual: " + e.getMessage());
            // Continua com a eleição se não conseguir verificar o líder
        }
        
        iniciouEleicao = true;
        maiorParametroRecebido = parametroEleicao;
        
        System.out.println(TerminalColors.BLUE + "[ELEIÇÃO-SOCKET] Processo " + processoId + 
                         " iniciando eleição com parâmetro " + parametroEleicao + TerminalColors.RESET);
        
        // Envia mensagem para o próximo processo no anel
        enviarMensagemEleicao(processoId, parametroEleicao);
    }
    
    /**
     * Verifica se um processo específico está ativo
     */
    private boolean verificarSeProcessoEstaAtivo(int processoId) {
        int portaDestino = PORTA_BASE_ELEICAO + processoId;
        try {
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress("localhost", portaDestino), 1000); // Timeout rápido de 1s
            socket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Envia mensagem de eleição para o próximo processo no anel
     */
    private void enviarMensagemEleicao(int iniciador, int parametro) {
        enviarMensagemEleicaoParaProcesso(iniciador, parametro, obterProximoProcessoId(), new java.util.HashSet<>());
    }
    
    /**
     * Envia mensagem de eleição para um processo específico
     */
    private void enviarMensagemEleicaoParaProcesso(int iniciador, int parametro, int processoDestino, java.util.Set<Integer> processosTentados) {
        // Evita tentar o mesmo processo múltiplas vezes
        if (processosTentados.contains(processoDestino)) {
            System.out.println(TerminalColors.YELLOW + "[ELEIÇÃO-SOCKET] Processo " + processoDestino + " já foi tentado, pulando..." + TerminalColors.RESET);
            tentarProximoProcessoAtivo(iniciador, parametro, processoDestino, processosTentados);
            return;
        }
        
        // Evita enviar para si mesmo (exceto se for o único processo ativo)
        if (processoDestino == processoId && processosTentados.size() < PROCESSOS_ANEL.length - 1) {
            System.out.println(TerminalColors.YELLOW + "[ELEIÇÃO-SOCKET] Evitando enviar para si mesmo, tentando próximo..." + TerminalColors.RESET);
            tentarProximoProcessoAtivo(iniciador, parametro, processoDestino, processosTentados);
            return;
        }
        
        processosTentados.add(processoDestino);
        int portaDestino = PORTA_BASE_ELEICAO + processoDestino;
        
        executor.submit(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new java.net.InetSocketAddress("localhost", portaDestino), TIMEOUT_CONEXAO);
                
                try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    
                    // Formato da mensagem: ELEICAO:iniciador:parametro
                    String mensagem = "ELEICAO:" + iniciador + ":" + parametro;
                    out.println(mensagem);
                    
                    System.out.println(TerminalColors.CYAN + "[ELEIÇÃO-SOCKET] Processo " + processoId + 
                                     " enviou eleição para Processo " + processoDestino + 
                                     " (iniciador=" + iniciador + ", parâmetro=" + parametro + ")" + TerminalColors.RESET);
                    
                } finally {
                    socket.close();
                }
                
            } catch (IOException e) {
                System.err.println("[ELEIÇÃO-SOCKET] Erro ao enviar mensagem de eleição para Processo " + 
                                 processoDestino + ": " + e.getMessage());
                
                // Tenta próximo processo ativo
                tentarProximoProcessoAtivo(iniciador, parametro, processoDestino, processosTentados);
            }
        });
    }
    
    /**
     * Processa mensagem de eleição recebida
     */
    private void processarMensagemEleicao(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            String mensagem = in.readLine();
            if (mensagem != null && mensagem.startsWith("ELEICAO:")) {
                String[] partes = mensagem.split(":");
                int iniciador = Integer.parseInt(partes[1]);
                int parametroRecebido = Integer.parseInt(partes[2]);
                
                System.out.println(TerminalColors.BLUE + "[ELEIÇÃO-SOCKET] Processo " + processoId + 
                                 " recebeu eleição (iniciador=" + iniciador + ", parâmetro=" + parametroRecebido + ")" + TerminalColors.RESET);
                
                // Se a mensagem voltou para o iniciador, determina o líder
                if (iniciador == processoId && iniciouEleicao) {
                    determinarLider(parametroRecebido);
                    return;
                }
                
                // Compara parâmetros e propaga
                int parametroParaPropagar = Math.max(parametroRecebido, parametroEleicao);
                
                System.out.println(TerminalColors.CYAN + "[ELEIÇÃO-SOCKET] Processo " + processoId + 
                                 " propagando eleição (parâmetro=" + parametroParaPropagar + ")" + TerminalColors.RESET);
                
                // Propaga para o próximo processo
                enviarMensagemEleicao(iniciador, parametroParaPropagar);
            }
            
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erro ao processar mensagem de eleição: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignora erro ao fechar socket
            }
        }
    }
    
    /**
     * Determina o líder baseado no maior parâmetro recebido
     */
    private void determinarLider(int parametroRecebido) {
        int maiorParametro = Math.max(parametroRecebido, parametroEleicao);
        int novoLider;
        
        // Determina qual processo tem o maior parâmetro
        if (maiorParametro == parametroEleicao) {
            novoLider = processoId;
        } else {
            // Encontra qual processo tem o parâmetro recebido
            novoLider = parametroRecebido / 10; // Parâmetro = processoId * 10
        }
        
        System.out.println(TerminalColors.GREEN + "[ELEIÇÃO-SOCKET] Processo " + processoId + 
                         " determinou líder: Processo " + novoLider + " (parâmetro=" + maiorParametro + ")" + TerminalColors.RESET);
        
        // Reseta estado da eleição
        iniciouEleicao = false;
        maiorParametroRecebido = 0;
        
        // Notifica o processo base sobre o novo líder
        try {
            processoBase.definirNovoLider(novoLider);
        } catch (Exception e) {
            System.err.println("Erro ao definir novo líder: " + e.getMessage());
        }
        
        // Anuncia o novo líder via multicast
        processoBase.anunciarLiderViaMulticast(novoLider);
    }
    
    /**
     * Obtém o ID do próximo processo no anel
     */
    private int obterProximoProcessoId() {
        // Anel: 1 -> 2 -> 3 -> 1
        switch (processoId) {
            case 1: return 2;
            case 2: return 3;
            case 3: return 1;
            default: return 1;
        }
    }
    
    /**
     * Tenta enviar para o próximo processo ativo quando há falha
     */
    private void tentarProximoProcessoAtivo(int iniciador, int parametro, int processoFalhou, java.util.Set<Integer> processosTentados) {
        int proximoTentativa = obterProximoProcessoAtivo(processoFalhou, processosTentados);
        
        if (proximoTentativa == -1) {
            // Todos os processos foram tentados ou sou o único ativo - assumo liderança
            System.out.println(TerminalColors.PURPLE + "[ELEIÇÃO-SOCKET] Processo " + processoId + 
                             " é o único ativo ou todos foram tentados - assumindo liderança" + TerminalColors.RESET);
            determinarLider(parametro);
            return;
        }
        
        if (proximoTentativa == processoId) {
            // A mensagem voltou para mim - determino o líder
            System.out.println(TerminalColors.PURPLE + "[ELEIÇÃO-SOCKET] Mensagem voltou para o iniciador - determinando líder" + TerminalColors.RESET);
            determinarLider(parametro);
            return;
        }
        
        System.out.println(TerminalColors.YELLOW + "[ELEIÇÃO-SOCKET] Tentando próximo processo ativo: " + 
                         proximoTentativa + TerminalColors.RESET);
        
        // Tenta enviar para o próximo processo
        enviarMensagemEleicaoParaProcesso(iniciador, parametro, proximoTentativa, processosTentados);
    }
    
    /**
     * Método de compatibilidade para chamadas antigas
     */
    private void tentarProximoProcessoAtivo(int iniciador, int parametro, int processoFalhou) {
        tentarProximoProcessoAtivo(iniciador, parametro, processoFalhou, new java.util.HashSet<>());
    }
    
    /**
     * Obtém o próximo processo ativo no anel, evitando os que falharam ou já foram tentados
     */
    private int obterProximoProcessoAtivo(int processoFalhou, java.util.Set<Integer> processosTentados) {
        // Encontra a posição atual no anel
        int posicaoAtual = -1;
        for (int i = 0; i < PROCESSOS_ANEL.length; i++) {
            if (PROCESSOS_ANEL[i] == processoId) {
                posicaoAtual = i;
                break;
            }
        }
        
        if (posicaoAtual == -1) {
            return -1; // Erro: processo não encontrado no anel
        }
        
        // Procura o próximo processo ativo no anel
        for (int i = 1; i < PROCESSOS_ANEL.length; i++) {
            int proximaPosicao = (posicaoAtual + i) % PROCESSOS_ANEL.length;
            int proximoProcesso = PROCESSOS_ANEL[proximaPosicao];
            
            // Pula processos que falharam ou já foram tentados
            if (proximoProcesso != processoFalhou && !processosTentados.contains(proximoProcesso)) {
                return proximoProcesso;
            }
        }
        
        // Se chegou aqui, todos os outros processos falharam ou foram tentados
        return -1;
    }
    
    /**
     * Obtém o próximo processo no anel, pulando o que falhou (método de compatibilidade)
     */
    private int obterProximoProcessoId(int processoFalhou) {
        int proximo = obterProximoProcessoId();
        if (proximo == processoFalhou) {
            // Pula o processo que falhou
            switch (proximo) {
                case 1: return 2;
                case 2: return 3;
                case 3: return 1;
                default: return processoId;
            }
        }
        return proximo;
    }
    
    /**
     * Finaliza o gerenciador de eleição
     */
    public void finalizar() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
            }
        } catch (IOException e) {
            System.err.println("Erro ao finalizar gerenciador de eleição: " + e.getMessage());
        }
    }
    
    /**
     * Verifica se este processo iniciou a eleição atual
     */
    public boolean iniciouEleicaoAtual() {
        return iniciouEleicao;
    }
}