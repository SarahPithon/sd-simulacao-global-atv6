package br.com.utils;

import br.com.models.Processo;
import br.com.models.Mensagem;
import br.com.models.TipoMensagem;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



/**
 * Classe responsável por gerenciar comunicação multicast entre processos
 */
public class GerenciadorMulticast {
    
    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int DEFAULT_MULTICAST_PORT = 50000;
    private final int multicastPort;
    private MulticastSocket socket;
    private InetAddress group;
    private NetworkInterface networkInterface;
    private ExecutorService executor;
    private volatile boolean escutando = false;
    private Processo processo; // Referência para o processo
    private LiderCallback liderCallback; // Callback para processar mensagens de liderança
    
    public GerenciadorMulticast() throws IOException {
        this(DEFAULT_MULTICAST_PORT);
    }
    
    public GerenciadorMulticast(int porta) throws IOException {
        this.multicastPort = porta;
        this.socket = new MulticastSocket(this.multicastPort);
        this.group = InetAddress.getByName(MULTICAST_ADDRESS);
        this.executor = Executors.newSingleThreadExecutor();
        this.processo = null; // Sem integração com processo
        this.liderCallback = null; // Sem callback de líder
        
        // Configurações de socket da versão funcional
        socket.setReuseAddress(true);
        socket.setTimeToLive(1);
        
        // Configurar interface de rede
        try {
            this.networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            if (this.networkInterface == null) {
                // Fallback para primeira interface disponível
                this.networkInterface = NetworkInterface.getNetworkInterfaces().nextElement();
            }
        } catch (Exception e) {
            System.err.println("[MULTICAST] Aviso: Não foi possível configurar interface de rede específica");
        }
    }
    
    /**
     * Construtor com integração ao processo
     */
    public GerenciadorMulticast(Processo processo) throws IOException {
        this(processo, DEFAULT_MULTICAST_PORT);
    }
    
    public GerenciadorMulticast(Processo processo, int porta) throws IOException {
        this.multicastPort = porta;
        this.socket = new MulticastSocket(this.multicastPort);
        this.group = InetAddress.getByName(MULTICAST_ADDRESS);
        this.executor = Executors.newSingleThreadExecutor();
        this.processo = processo;
        this.liderCallback = null; // Sem callback de líder
        
        // Configurações de socket da versão funcional
        socket.setReuseAddress(true);
        socket.setTimeToLive(1);
        
        // Configurar interface de rede
        try {
            this.networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            if (this.networkInterface == null) {
                // Fallback para primeira interface disponível
                this.networkInterface = NetworkInterface.getNetworkInterfaces().nextElement();
            }
        } catch (Exception e) {
            System.err.println("[MULTICAST] Aviso: Não foi possível configurar interface de rede específica");
        }
    }
    
    /**
     * Construtor com callback para processar mensagens de liderança (usado por ProcessoRMIBase)
     */
    public GerenciadorMulticast(LiderCallback liderCallback) throws IOException {
        this(liderCallback, DEFAULT_MULTICAST_PORT);
    }
    
    public GerenciadorMulticast(LiderCallback liderCallback, int porta) throws IOException {
        this.multicastPort = porta;
        this.socket = new MulticastSocket(this.multicastPort);
        this.group = InetAddress.getByName(MULTICAST_ADDRESS);
        this.executor = Executors.newSingleThreadExecutor();
        this.processo = null;
        this.liderCallback = liderCallback;
        
        // Configurações de socket da versão funcional
        socket.setReuseAddress(true);
        socket.setTimeToLive(1);
        
        // Configurar interface de rede
        try {
            this.networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            if (this.networkInterface == null) {
                // Fallback para primeira interface disponível
                this.networkInterface = NetworkInterface.getNetworkInterfaces().nextElement();
            }
        } catch (Exception e) {
            System.err.println("[MULTICAST] Aviso: Não foi possível configurar interface de rede específica");
        }
    }
    
    /**
     * Inicia a escuta de mensagens multicast
     */
    public void iniciarEscuta(String nomeProcesso) throws IOException {
        if (escutando) {
            return;
        }
        
        // Juntar ao grupo multicast
        if (networkInterface != null) {
            socket.joinGroup(new InetSocketAddress(group, multicastPort), networkInterface);
        } else {
            socket.joinGroup(group);
        }
        
        escutando = true;
        
        System.out.println(TerminalColors.multicastMessage(
            "[MULTICAST] Configurado para grupo " + MULTICAST_ADDRESS + ":" + multicastPort));
        
        // Usar Thread simples como na versão funcional
        Thread escutaThread = new Thread(() -> {
            System.out.println(TerminalColors.multicastMessage(
                "[MULTICAST] Thread de escuta iniciada para processo: " + nomeProcesso));
            byte[] buffer = new byte[1024];
            
            while (!Thread.currentThread().isInterrupted() && escutando) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    String mensagem = new String(packet.getData(), 0, packet.getLength());
                    processarMensagemRecebida(mensagem, nomeProcesso);
                    
                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted() && escutando) {
                        System.err.println(TerminalColors.errorMessage(
                            "[ERRO] Erro ao receber mensagem multicast: " + e.getMessage()));
                        e.printStackTrace();
                    }
                    break;
                }
            }
            System.out.println(TerminalColors.multicastMessage(
                "[MULTICAST] Thread de escuta finalizada para processo: " + nomeProcesso));
        });
        
        escutaThread.setDaemon(true);
        escutaThread.setName("MulticastListener-" + nomeProcesso);
        escutaThread.start();
    }
    
    /**
     * Envia notificação de falha via multicast (método depreciado)
     * Agora as notificações de falha são enviadas via HEARTBEAT_ALERT
     */
    @Deprecated
    public void enviarNotificacaoFalha(String nomeProcessoFalhado, String nomeRemetente) {
        // Método depreciado - notificações agora são enviadas via HEARTBEAT_ALERT
        // para evitar duplicação de mensagens
    }
    
    /**
     * Envia uma mensagem via multicast
     */
    public void enviarMensagem(String mensagem) throws IOException {
        byte[] buffer = mensagem.getBytes();
        DatagramPacket packet = new DatagramPacket(
            buffer, buffer.length, group, multicastPort);
        
        socket.send(packet);
    }
    
    /**
     * Envia uma mensagem via multicast apenas se o processo for líder
     */
    public void enviarMensagemComoLider(String mensagem, String nomeProcesso, boolean isLider) throws IOException {
        if (!isLider) {
            System.out.println(TerminalColors.warningMessage(
                "[MULTICAST] Apenas o líder pode enviar mensagens via multicast. " + nomeProcesso + " não é o líder."));
            return;
        }
        
        String mensagemCompleta = "MENSAGEM_LIDER:" + nomeProcesso + ":" + mensagem;
        enviarMensagem(mensagemCompleta);
        System.out.println(TerminalColors.successMessage(
            "[MULTICAST] Mensagem enviada pelo líder " + nomeProcesso + ": " + mensagem));
    }
    
    /**
     * Processa mensagens recebidas via multicast
     */
    private void processarMensagemRecebida(String mensagem, String nomeProcesso) {
        try {
            // Removido processamento de mensagens [FALHA] para evitar duplicação
            // Agora apenas HEARTBEAT_ALERT é usado para notificações de falha
            if (mensagem.startsWith("HEARTBEAT_OK:")) {
                String[] partes = mensagem.split(":", 3);
                if (partes.length >= 3) {
                    String processoLider = partes[1];
                    String detalhes = partes[2];
                    
                    // Não processar mensagens enviadas por este próprio processo
                    if (!processoLider.equals(nomeProcesso)) {
                        System.out.println(TerminalColors.successMessage(
                            "[MULTICAST] Heartbeat OK recebido do líder " + processoLider + ": " + detalhes));
                        
                        // Adicionar à lista de mensagens recebidas se processo estiver disponível
                        adicionarMensagemRecebida("HEARTBEAT_OK", processoLider, "Heartbeat OK: " + detalhes);
                    }
                }
            } else if (mensagem.startsWith("HEARTBEAT_ALERT:")) {
                String[] partes = mensagem.split(":", 3);
                if (partes.length >= 3) {
                    String processoLider = partes[1];
                    String detalhes = partes[2];
                    
                    // Não processar mensagens enviadas por este próprio processo
                    if (!processoLider.equals(nomeProcesso)) {
                        // Extrai os processos que falharam da mensagem
                        if (detalhes.contains("Processos com falha: ")) {
                            String processosFalhados = detalhes.substring(detalhes.indexOf("Processos com falha: ") + "Processos com falha: ".length());
                            System.out.println(TerminalColors.errorMessage(
                                "[MULTICAST] Processo(s) " + processosFalhados + " saiu(ram) do ar"));
                        } else {
                            System.out.println(TerminalColors.warningMessage(
                                "[MULTICAST] Heartbeat ALERT recebido do líder " + processoLider + ": " + detalhes));
                        }
                        
                        // Adicionar à lista de mensagens recebidas se processo estiver disponível
                        adicionarMensagemRecebida("HEARTBEAT_ALERT", processoLider, detalhes);
                    }
                }
            } else if (mensagem.startsWith("MENSAGEM_LIDER:")) {
                String[] partes = mensagem.split(":", 3);
                if (partes.length >= 3) {
                    String processoLider = partes[1];
                    String conteudoMensagem = partes[2];
                    
                    // Não processar mensagens enviadas por este próprio processo
                    if (!processoLider.equals(nomeProcesso)) {
                        System.out.println(TerminalColors.multicastMessage(
                            "[MULTICAST] Mensagem do líder " + processoLider + ": " + conteudoMensagem));
                        
                        // Adicionar à lista de mensagens recebidas se processo estiver disponível
                        adicionarMensagemRecebida("MENSAGEM_LIDER", processoLider, conteudoMensagem);
                    }
                }
            } else if (mensagem.startsWith("NOVO_LIDER:")) {
                String[] partes = mensagem.split(":", 2);
                if (partes.length >= 2) {
                    try {
                        int novoLiderId = Integer.parseInt(partes[1]);
                        
                        // Processar apenas se temos callback de líder configurado
                        if (liderCallback != null) {
                            int meuId = liderCallback.getProcessoId();
                            
                            System.out.println(TerminalColors.GREEN + 
                                "[MULTICAST] Mensagem NOVO_LIDER recebida: ProcessoRMI" + novoLiderId + 
                                " é o novo líder" + TerminalColors.RESET);
                            
                            // Define o novo líder através do callback
                            liderCallback.definirNovoLider(novoLiderId);
                            
                            // Adicionar à lista de mensagens recebidas
                            adicionarMensagemRecebida("NOVO_LIDER", "ProcessoRMI" + novoLiderId, 
                                "Novo líder eleito: ProcessoRMI" + novoLiderId);
                        } else {
                            System.out.println(TerminalColors.multicastMessage(
                                "[MULTICAST] Mensagem NOVO_LIDER recebida: ProcessoRMI" + novoLiderId + 
                                " é o novo líder (sem callback configurado)"));
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("[MULTICAST] Erro ao processar ID do líder: " + partes[1]);
                    }
                }
            } else {
                System.out.println(TerminalColors.multicastMessage(
                    "[MULTICAST] Mensagem recebida: " + mensagem));
                
                // Adicionar mensagem genérica à lista se processo estiver disponível
                adicionarMensagemRecebida("MULTICAST", "Desconhecido", mensagem);
            }
        } catch (Exception e) {
            System.err.println("[MULTICAST] Erro ao processar mensagem: " + e.getMessage());
        }
    }
    
    /**
     * Adiciona mensagem multicast à lista de mensagens recebidas do processo
     */
    private void adicionarMensagemRecebida(String tipo, String remetente, String conteudo) {
        if (processo != null) {
            try {
                // Atualizar clock do processo
                processo.setClockProcesso(processo.getClockProcesso() + 1);
                
                // Criar mensagem multicast
                Mensagem mensagemMulticast = new Mensagem();
                mensagemMulticast.setIdMensagem(UUID.randomUUID());
                mensagemMulticast.setProcessoRemetente(remetente);
                mensagemMulticast.setProcessoDestinatario(processo.getNomeProcesso());
                mensagemMulticast.setDataMensagem(LocalDateTime.now());
                mensagemMulticast.setClockMensagem(processo.getClockProcesso());
                mensagemMulticast.setConteudoMensagem("[" + tipo + "] " + conteudo);
                mensagemMulticast.setTipoMensagem(TipoMensagem.MULTICAST);
                
                // Adicionar à lista de mensagens recebidas
                processo.getMensagensRecebidas().add(mensagemMulticast);
            } catch (Exception e) {
                System.err.println("[MULTICAST] Erro ao adicionar mensagem à lista: " + e.getMessage());
            }
        }
    }
    
    /**
     * Para a escuta e fecha recursos
     */
    public void pararEscuta() {
        escutando = false;
        
        try {
            if (networkInterface != null) {
                socket.leaveGroup(new InetSocketAddress(group, multicastPort), networkInterface);
            } else {
                socket.leaveGroup(group);
            }
        } catch (IOException e) {
            System.err.println("[MULTICAST] Erro ao sair do grupo: " + e.getMessage());
        }
        
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
    
    /**
     * Fecha o socket multicast e sai do grupo
     */
    public void fechar() {
        try {
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            if (networkInterface != null) {
                socket.leaveGroup(new InetSocketAddress(group, multicastPort), networkInterface);
            } else {
                socket.leaveGroup(group);
            }
            socket.close();
        } catch (IOException e) {
            System.err.println(TerminalColors.errorMessage("[ERRO] Erro ao fechar multicast: " + e.getMessage()));
        }
    }
}