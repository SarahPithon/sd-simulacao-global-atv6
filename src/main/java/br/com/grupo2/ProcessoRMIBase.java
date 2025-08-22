package br.com.grupo2;

import br.com.models.Mensagem;
import br.com.models.TipoMensagem;
import br.com.utils.TerminalColors;
import br.com.utils.GerenciadorMulticast;
import br.com.utils.LiderCallback;
import br.com.utils.SupercoordenadorCallback;
import br.com.utils.GerenciadorComunicacaoIntergrupos;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Classe base para processos RMI que implementa o algoritmo de eleição em anel
 */
public abstract class ProcessoRMIBase extends UnicastRemoteObject implements ProcessoRMIInterface, LiderCallback {
    
    protected int processoId;
    protected int parametroEleicao; // Parâmetro usado para eleição (processo mais forte)
    protected int liderAtual;
    protected boolean estaAtivo;
    protected ProcessoRMIInterface proximoProcesso;
    protected boolean eleicaoEmAndamento;
    protected List<Integer> candidatosEleicao;
    protected int parametroEleicaoAtual; // Maior parâmetro encontrado na eleição atual
    protected int processoIniciadorEleicao; // Processo que iniciou a eleição
    protected ScheduledExecutorService scheduler;
    protected long ultimoHeartbeat;
    protected static final long TIMEOUT_HEARTBEAT = 15000; // 15 segundos
    protected static final long INTERVALO_PING_PONG = 10000; // 10 segundos
    protected GerenciadorMulticast multicastManager;
    protected boolean respondeuPing = false;
    protected long ultimoPingEnviado = 0;
    protected int processosAtivos = 0;
    protected GerenciadorEleicaoSocket gerenciadorEleicaoSocket;
    protected boolean supercoordenador = false; // Indica se este processo é o supercoordenador
    protected GerenciadorComunicacaoIntergrupos gerenciadorIntergrupos;
    protected SupercoordenadorCallback callbackImpl;
    
    public ProcessoRMIBase(int processoId) throws RemoteException {
        super();
        this.processoId = processoId;
        // Define parâmetro de eleição baseado no ID (ProcessoRMI1=10, ProcessoRMI2=20, ProcessoRMI3=30)
        this.parametroEleicao = processoId * 10;
        this.liderAtual = 3; // ProcessoRMI3 é o líder inicial
        this.estaAtivo = true;
        this.eleicaoEmAndamento = false;
        this.candidatosEleicao = new ArrayList<>();
        this.parametroEleicaoAtual = 0;
        this.processoIniciadorEleicao = -1;
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.ultimoHeartbeat = System.currentTimeMillis();
        
        // Inicializar multicastManager
        try {
            this.multicastManager = new GerenciadorMulticast(this, 50002); // Porta específica do grupo 2
            System.out.println(TerminalColors.CYAN + "[Processo " + processoId + "] Multicast manager inicializado" + TerminalColors.RESET);
        } catch (Exception e) {
            System.err.println("Erro ao inicializar multicast manager: " + e.getMessage());
            this.multicastManager = null;
        }
        
        // Inicializar gerenciador de eleição via sockets
        try {
            this.gerenciadorEleicaoSocket = new GerenciadorEleicaoSocket(processoId, parametroEleicao, this);
            System.out.println(TerminalColors.CYAN + "[Processo " + processoId + "] Gerenciador de eleição via socket inicializado" + TerminalColors.RESET);
        } catch (Exception e) {
            System.err.println("Erro ao inicializar gerenciador de eleição via socket: " + e.getMessage());
            this.gerenciadorEleicaoSocket = null;
        }
        
        // Inicializar comunicação intergrupos
        try {
            this.callbackImpl = new SupercoordenadorCallbackImpl();
            this.gerenciadorIntergrupos = new GerenciadorComunicacaoIntergrupos(2, "ProcessoRMI" + processoId, callbackImpl);
            this.gerenciadorIntergrupos.iniciar();
        } catch (Exception e) {
            System.err.println("Erro ao inicializar comunicação intergrupos: " + e.getMessage());
        }
        
        System.out.println(TerminalColors.CYAN + "[Processo " + processoId + "] Iniciado com parâmetro de eleição: " + 
                          parametroEleicao + " (Líder inicial: ProcessoRMI3)" + TerminalColors.RESET);
        
        // Se for o líder (ProcessoRMI3), inicia sistema de ping/pong
        if (processoId == 3) {
            iniciarSistemaPingPongComoLider();
        }
        
        // Inicia monitoramento de heartbeat
        iniciarMonitoramentoHeartbeat();
    }
    
    @Override
    public void receberMensagem(Mensagem mensagem) throws RemoteException {
        // Processa diferentes tipos de mensagem
        switch (mensagem.getTipoMensagem()) {
            case PING:
                // Processos não-líderes não exibem mensagens de PING recebido
                responderPong(Integer.parseInt(mensagem.getProcessoRemetente()));
                break;
            case PONG:
                // Apenas o líder processa PONGs (incrementa contador)
                if (liderAtual == processoId) {
                    processosAtivos++;
                }
                break;
            case ELEICAO:
                // Mensagens de eleição são tratadas pelos métodos específicos
                break;
            default:
                // Exibe mensagem genérica apenas para outros tipos
                System.out.println(TerminalColors.CYAN + "[Processo " + processoId + "] Mensagem recebida: " + 
                                  mensagem.getConteudoMensagem() + TerminalColors.RESET);
                processarMensagemEspecifica(mensagem);
                break;
        }
    }
    
    @Override
    public void iniciarEleicao(int processoIniciador, List<Integer> candidatos) throws RemoteException {
        if (eleicaoEmAndamento) {
            System.out.println(TerminalColors.YELLOW + "[Processo " + processoId + "] Eleição já em andamento" + TerminalColors.RESET);
            return;
        }
        
        System.out.println(TerminalColors.BLUE + "[Processo " + processoId + "] Iniciando eleição em anel" + TerminalColors.RESET);
        eleicaoEmAndamento = true;
        processoIniciadorEleicao = processoId; // Este processo está iniciando a eleição
        parametroEleicaoAtual = parametroEleicao; // Começa com seu próprio parâmetro
        
        System.out.println(TerminalColors.CYAN + "[Processo " + processoId + "] Enviando parâmetro " + 
                          parametroEleicao + " para o próximo processo" + TerminalColors.RESET);
        
        // Inicia eleição diretamente via socket
        iniciarEleicaoViaSocket();
    }
    
    @Override
    public void propagarEleicao(List<Integer> candidatos) throws RemoteException {
        // Método mantido para compatibilidade, mas redireciona para o novo algoritmo
        if (!candidatos.isEmpty()) {
            propagarEleicaoComParametro(candidatos.get(0), parametroEleicao);
        }
    }
    
    /**
     * Novo método para propagar eleição com parâmetros de eleição
     */
    public void propagarEleicaoComParametro(int iniciador, int parametroRecebido) throws RemoteException {
        System.out.println(TerminalColors.BLUE + "[Processo " + processoId + "] Recebeu eleição do iniciador " + 
                          iniciador + " com parâmetro " + parametroRecebido + TerminalColors.RESET);
        
        // Se a eleição voltou para o iniciador, determina o líder
        if (iniciador == processoId) {
            // A eleição completou uma volta - determina o líder baseado no maior parâmetro
            int parametroVencedor = Math.max(parametroRecebido, parametroEleicao);
            int liderEleito = (parametroVencedor == parametroEleicao) ? processoId : 
                             (parametroRecebido > parametroEleicao ? iniciador : processoId);
            
            System.out.println(TerminalColors.GREEN + "[Processo " + processoId + "] Eleição completada! " +
                              "Parâmetro vencedor: " + parametroVencedor + ", Líder eleito: Processo " + liderEleito + TerminalColors.RESET);
            
            anunciarLiderViaMulticast(liderEleito);
            eleicaoEmAndamento = false; // Finaliza a eleição
            return;
        }
        
        // Evita processar a mesma eleição múltiplas vezes
        if (eleicaoEmAndamento && processoIniciadorEleicao == iniciador) {
            System.out.println(TerminalColors.YELLOW + "[Processo " + processoId + "] Eleição já em andamento do mesmo iniciador - ignorando" + TerminalColors.RESET);
            return;
        }
        
        eleicaoEmAndamento = true;
        processoIniciadorEleicao = iniciador;
        
        // Compara parâmetros e propaga o maior
        int parametroParaPropagar = Math.max(parametroRecebido, parametroEleicao);
        
        System.out.println(TerminalColors.CYAN + "[Processo " + processoId + "] Propagando parâmetro " + 
                          parametroParaPropagar + " (recebido: " + parametroRecebido + ", próprio: " + parametroEleicao + ")" + TerminalColors.RESET);
        
        // Usa o gerenciador de eleição via socket para iniciar nova eleição
        if (gerenciadorEleicaoSocket != null) {
            gerenciadorEleicaoSocket.iniciarEleicao();
        } else {
            System.err.println("[Processo " + processoId + "] Gerenciador de eleição via socket não inicializado");
        }
    }
    
    @Override
    public void anunciarLider(int novoLider) throws RemoteException {
        if (liderAtual != novoLider) {
            System.out.println(TerminalColors.GREEN + "[Processo " + processoId + "] Novo líder eleito: Processo " + 
                              novoLider + TerminalColors.RESET);
            liderAtual = novoLider;
        }
        
        eleicaoEmAndamento = false;
        candidatosEleicao.clear();
        
        // Anuncia o líder via multicast para todos os processos
        if (processoId != novoLider) {
            anunciarLiderViaMulticast(novoLider);
        }
    }
    

    
    @Override
    public boolean estaAtivo() throws RemoteException {
        return estaAtivo;
    }
    
    @Override
    public int getProcessoId() throws RemoteException {
        return processoId;
    }
    
    @Override
    public int getLiderAtual() throws RemoteException {
        return liderAtual;
    }
    
    @Override
    public void setProximoProcesso(ProcessoRMIInterface proximoProcesso) throws RemoteException {
        this.proximoProcesso = proximoProcesso;
        System.out.println(TerminalColors.CYAN + "[Processo " + processoId + "] Próximo processo configurado" + TerminalColors.RESET);
    }
    
    @Override
    public void enviarHeartbeat() throws RemoteException {
        if (proximoProcesso != null) {
            try {
                proximoProcesso.receberHeartbeat(processoId);
            } catch (RemoteException e) {
                System.err.println("Erro ao enviar heartbeat: " + e.getMessage());
                iniciarEleicaoViaSocket();
            }
        }
    }
    
    @Override
    public void receberHeartbeat(int remetenteId) throws RemoteException {
        ultimoHeartbeat = System.currentTimeMillis();
        // Apenas o líder exibe mensagens de heartbeat recebidas
        // Processos não-líderes operam silenciosamente
    }
    
    protected void iniciarMonitoramentoHeartbeat() {
        // Envia heartbeat periodicamente apenas se o próximo processo estiver configurado
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (proximoProcesso != null) {
                    enviarHeartbeat();
                }
            } catch (RemoteException e) {
                System.err.println("Erro no heartbeat periódico: " + e.getMessage());
            }
        }, 2, 3, TimeUnit.SECONDS);
        
        // Monitora timeout de heartbeat apenas para informação
        scheduler.scheduleAtFixedRate(() -> {
            long agora = System.currentTimeMillis();
            // Só verifica timeout se o próximo processo estiver configurado
            if (proximoProcesso != null && agora - ultimoHeartbeat > TIMEOUT_HEARTBEAT) {
                // Evita spam de mensagens - só mostra a cada 30 segundos
                if ((agora - ultimoHeartbeat) % 30000 < 2000) {
                    System.out.println(TerminalColors.YELLOW + "[Processo " + processoId + "] Possível timeout de heartbeat detectado - aguardando estabilização" + TerminalColors.RESET);
                }
            }
        }, 5, 2, TimeUnit.SECONDS);
    }
    

    
    /**
     * Descobre quais processos estão ativos no sistema
     */
    private List<Integer> descobrirProcessosAtivos() {
        List<Integer> processosAtivos = new ArrayList<>();
        
        // Testa todos os processos possíveis (1, 2, 3)
        for (int i = 1; i <= 3; i++) {
            if (i == processoId) {
                processosAtivos.add(i); // O próprio processo está ativo
                continue;
            }
            
            try {
                String url = "rmi://localhost:110" + i + "/ProcessoRMI" + i;
                ProcessoRMIInterface processo = (ProcessoRMIInterface) Naming.lookup(url);
                
                if (processo.estaAtivo()) {
                    processosAtivos.add(i);
                    System.out.println(TerminalColors.CYAN + "[Processo " + processoId + "] ProcessoRMI" + i + " está ativo" + TerminalColors.RESET);
                }
            } catch (Exception e) {
                System.out.println(TerminalColors.YELLOW + "[Processo " + processoId + "] ProcessoRMI" + i + " não está disponível" + TerminalColors.RESET);
            }
        }
        
        return processosAtivos;
    }
    
    /**
     * Encontra o ID do próximo processo ativo no anel
     */
    private Integer encontrarProximoProcessoAtivoId(List<Integer> processosAtivos) {
        if (processosAtivos.size() <= 1) {
            return null; // Só há um processo ativo (este)
        }
        
        // Ordena a lista para garantir ordem consistente
        Collections.sort(processosAtivos);
        
        // Encontra o próximo processo após o atual
        for (int i = 0; i < processosAtivos.size(); i++) {
            if (processosAtivos.get(i) == processoId) {
                // Retorna o próximo processo na lista (circular)
                return processosAtivos.get((i + 1) % processosAtivos.size());
            }
        }
        
        // Se não encontrou o processo atual na lista, retorna o primeiro
        return processosAtivos.get(0);
    }
    
    /**
     * Encontra o próximo processo ativo no anel e retorna sua interface
     */

    
    /**
     * Anuncia o líder para todos os processos ativos quando a propagação normal falha
     */

    
    private void determinarLiderEleicao(int iniciador, int parametroRecebido) {
        int maiorParametro = Math.max(parametroRecebido, parametroEleicao);
        int novoLider = maiorParametro == parametroEleicao ? processoId : iniciador;
        
        System.out.println(TerminalColors.PURPLE + "[Processo " + processoId + "] Determinando líder: parâmetro recebido=" + parametroRecebido + ", próprio=" + parametroEleicao + ", novo líder=ProcessoRMI" + novoLider + TerminalColors.RESET);
        
        eleicaoEmAndamento = false;
        liderAtual = novoLider;
        
        // Anuncia o novo líder
        anunciarLiderViaMulticast(novoLider);
        
        if (novoLider == processoId) {
            System.out.println(TerminalColors.GREEN + "[ELEIÇÃO] ProcessoRMI" + processoId + " foi eleito como novo líder!" + TerminalColors.RESET);
            
            // Verificar se deve ser supercoordenador (grupo 2 só se torna se grupo 1 não tiver líder)
            // Por simplicidade, grupo 2 não será supercoordenador automaticamente
            // A lógica de supercoordenador será gerenciada pela comunicação intergrupos
            
            iniciarSistemaPingPongComoLider();
        } else {
            System.out.println(TerminalColors.BLUE + "[ELEIÇÃO] ProcessoRMI" + novoLider + " foi eleito como novo líder!" + TerminalColors.RESET);
        }
    }


    
    /**
     * Inicia eleição via sockets quando detecta falha do líder
     */
    protected void iniciarEleicaoViaSocket() {
        if (gerenciadorEleicaoSocket != null && !eleicaoEmAndamento) {
            eleicaoEmAndamento = true;
            System.out.println(TerminalColors.BLUE + "[Processo " + processoId + "] Iniciando eleição via socket devido à falha do líder" + TerminalColors.RESET);
            gerenciadorEleicaoSocket.iniciarEleicao();
        } else if (eleicaoEmAndamento) {
            System.out.println(TerminalColors.YELLOW + "[Processo " + processoId + "] Eleição já em andamento" + TerminalColors.RESET);
        } else {
            System.err.println("[Processo " + processoId + "] Gerenciador de eleição via socket não disponível");
        }
    }
    
    /**
     * Notifica falha de processo via multicast
     */
    private void notificarFalhaViaMulticast() {
        try {
            // Cria mensagem de notificação de falha
            Mensagem mensagemFalha = new Mensagem();
            mensagemFalha.setProcessoRemetente(String.valueOf(processoId));
            mensagemFalha.setProcessoDestinatario("TODOS");
            mensagemFalha.setConteudoMensagem("FALHA_DETECTADA:PROXIMO_PROCESSO");
            mensagemFalha.setTipoMensagem(TipoMensagem.PROCESSO_FALHOU);
            
            // Envia via multicast se disponível
            if (multicastManager != null) {
                GerenciadorMulticast multicast = new GerenciadorMulticast(50002); // Porta específica do grupo 2
                String mensagemTexto = "FALHA_DETECTADA:PROXIMO_PROCESSO";
                multicast.enviarMensagem(mensagemTexto);
            }
            
            System.out.println(TerminalColors.YELLOW + "[Processo " + processoId + "] Notificação de falha enviada via multicast" + TerminalColors.RESET);
            
        } catch (Exception e) {
            System.err.println("Erro ao notificar falha via multicast: " + e.getMessage());
        }
    }
    
    /**
     * Inicia o sistema de ping/pong como líder
     */
    protected void iniciarSistemaPingPongComoLider() {
        System.out.println(TerminalColors.GREEN + "[Processo " + processoId + "] Iniciando sistema PING/PONG como líder" + TerminalColors.RESET);
        
        // Envia PING a cada 10 segundos
        scheduler.scheduleAtFixedRate(() -> {
            if (liderAtual == processoId) {
                enviarPingParaTodos();
            }
        }, 5000, INTERVALO_PING_PONG, TimeUnit.MILLISECONDS);
        
        // Verifica respostas PONG e envia status via multicast
        scheduler.scheduleAtFixedRate(() -> {
            if (liderAtual == processoId) {
                verificarStatusProcessos();
            }
        }, 8000, INTERVALO_PING_PONG, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Envia PING para todos os processos
     */
    private void enviarPingParaTodos() {
        processosAtivos = 0; // Reset contador
        ultimoPingEnviado = System.currentTimeMillis();
        
        System.out.println(TerminalColors.YELLOW + "[LÍDER] Enviando mensagens de heartbeat para os processos..." + TerminalColors.RESET);
        
        // Lista de processos para enviar PING
        String[] processosRMI = {"rmi://localhost:1101/ProcessoRMI1", "rmi://localhost:1102/ProcessoRMI2"};
        
        for (String urlProcesso : processosRMI) {
            try {
                ProcessoRMIInterface processo = (ProcessoRMIInterface) Naming.lookup(urlProcesso);
                
                // Cria mensagem PING
                br.com.models.Mensagem mensagemPing = new br.com.models.Mensagem();
                mensagemPing.setTipoMensagem(br.com.models.TipoMensagem.PING);
                mensagemPing.setProcessoRemetente(String.valueOf(processoId));
                mensagemPing.setConteudoMensagem("PING do líder");
                mensagemPing.setDataMensagem(java.time.LocalDateTime.now());
                mensagemPing.setIdMensagem(java.util.UUID.randomUUID());
                
                processo.receberMensagem(mensagemPing);
                // Não exibe mensagem individual para cada processo
                
            } catch (Exception e) {
                String nomeProcesso = extrairNomeProcesso(urlProcesso);
                System.out.println(TerminalColors.RED + "[LÍDER] Falha ao enviar heartbeat para " + nomeProcesso + ": " + e.getMessage() + TerminalColors.RESET);
                
                // Notifica falha via multicast
                if (multicastManager != null) {
                    try {
                        String mensagemFalha = "PROCESSO_FALHOU:" + nomeProcesso;
                        multicastManager.enviarMensagem(mensagemFalha);
                        System.out.println(TerminalColors.RED + "[LÍDER] Notificação de falha enviada via multicast para: " + nomeProcesso + TerminalColors.RESET);
                    } catch (Exception ex) {
                        System.err.println("Erro ao notificar falha via multicast: " + ex.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * Responde com PONG ao receber PING
     */
    private void responderPong(int liderProcessoId) {
        try {
            String urlLider = "rmi://localhost:110" + liderProcessoId + "/ProcessoRMI" + liderProcessoId;
            ProcessoRMIInterface lider = (ProcessoRMIInterface) Naming.lookup(urlLider);
            
            // Cria mensagem PONG
            br.com.models.Mensagem mensagemPong = new br.com.models.Mensagem();
            mensagemPong.setTipoMensagem(br.com.models.TipoMensagem.PONG);
            mensagemPong.setProcessoRemetente(String.valueOf(processoId));
            mensagemPong.setConteudoMensagem("PONG de ProcessoRMI" + processoId);
            mensagemPong.setDataMensagem(java.time.LocalDateTime.now());
            mensagemPong.setIdMensagem(java.util.UUID.randomUUID());
            
            lider.receberMensagem(mensagemPong);
            // Processos não-líderes não exibem mensagens de PONG enviado
            
        } catch (Exception e) {
            System.err.println("Erro ao enviar PONG: " + e.getMessage());
        }
    }
    
    /**
     * Verifica status dos processos e envia notificação via multicast
     */
    private void verificarStatusProcessos() {
        // Aguarda um tempo para receber todos os PONGs
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (processosAtivos >= 2) {
            // Todos os processos estão ativos - envia notificação multicast
            String mensagemStatus = "TODOS_PROCESSOS_ATIVOS:ProcessoRMI" + processoId + ":Sistema funcionando normalmente (" + processosAtivos + "/2 processos responderam)";
            if (multicastManager != null) {
                try {
                    multicastManager.enviarMensagem(mensagemStatus);
                    System.out.println(TerminalColors.GREEN + "[LÍDER] Todos os processos estão ativos - notificação enviada via multicast" + TerminalColors.RESET);
                } catch (java.io.IOException e) {
                    System.err.println("Erro ao enviar status via multicast: " + e.getMessage());
                }
            } else {
                System.out.println(TerminalColors.GREEN + "[LÍDER] Todos os processos estão ativos (" + processosAtivos + "/2)" + TerminalColors.RESET);
            }
        } else {
            // Alguns processos falharam
            String mensagemFalha = "HEARTBEAT_ALERT:ProcessoRMI" + processoId + ":Alguns processos não responderam (" + processosAtivos + "/2)";
            if (multicastManager != null) {
                try {
                    multicastManager.enviarMensagem(mensagemFalha);
                    System.out.println(TerminalColors.RED + "[LÍDER] Alerta enviado via multicast: Alguns processos falharam (" + processosAtivos + "/2)" + TerminalColors.RESET);
                } catch (java.io.IOException e) {
                    System.err.println("Erro ao enviar alerta via multicast: " + e.getMessage());
                }
            } else {
                System.out.println(TerminalColors.RED + "[LÍDER] Alguns processos falharam (" + processosAtivos + "/2)" + TerminalColors.RESET);
            }
        }
    }
    
    /**
     * Extrai o nome do processo da URL RMI
     */
    private String extrairNomeProcesso(String urlProcesso) {
        if (urlProcesso.contains("ProcessoRMI1")) return "ProcessoRMI1";
        if (urlProcesso.contains("ProcessoRMI2")) return "ProcessoRMI2";
        if (urlProcesso.contains("ProcessoRMI3")) return "ProcessoRMI3";
        return "ProcessoDesconhecido";
    }
    
    /**
     * Método abstrato para processar mensagens específicas de cada processo
     */
    protected abstract void processarMensagemEspecifica(Mensagem mensagem);
    

    
    /**
     * Método público para anunciar líder via multicast (usado pelo GerenciadorEleicaoSocket)
     */
    public void anunciarLiderViaMulticast(int novoLider) {
        try {
            if (multicastManager != null) {
                String mensagemLider = "NOVO_LIDER:" + novoLider;
                multicastManager.enviarMensagem(mensagemLider);
                System.out.println(TerminalColors.CYAN + "[Processo " + processoId + "] Anunciado novo líder via multicast: Processo " + novoLider + TerminalColors.RESET);
            } else {
                System.out.println(TerminalColors.YELLOW + "[Processo " + processoId + "] Multicast não disponível para anunciar líder" + TerminalColors.RESET);
            }
        } catch (Exception e) {
            System.err.println("Erro ao anunciar líder via multicast: " + e.getMessage());
        }
    }
    
    /**
      * Implementação da interface LiderCallback
      */
     @Override
     public void definirNovoLider(int novoLider) {
         if (liderAtual != novoLider) {
             System.out.println(TerminalColors.GREEN + "[Processo " + processoId + "] Novo líder recebido via multicast: Processo " + novoLider + TerminalColors.RESET);
             liderAtual = novoLider;
             
             // Se este processo se tornou líder, inicia sistema de ping/pong
             if (novoLider == processoId) {
                 System.out.println(TerminalColors.PURPLE + "[Processo " + processoId + "] Assumindo liderança via multicast - iniciando sistema ping/pong" + TerminalColors.RESET);
                 
                 // Verificar se deve ser supercoordenador (grupo 2 só se torna se grupo 1 não tiver líder)
                 // Por simplicidade, grupo 2 não será supercoordenador automaticamente
                 // A lógica de supercoordenador será gerenciada pela comunicação intergrupos
                 
                 iniciarSistemaPingPongComoLider();
             }
         }
         eleicaoEmAndamento = false;
      }
     

    
    public boolean isSupercoordenador() {
        return supercoordenador;
    }
    
    public void setSupercoordenador(boolean supercoordenador) {
        this.supercoordenador = supercoordenador;
    }
    
    /**
     * Finaliza o processo e libera recursos
     */
    public void finalizar() {
        estaAtivo = false;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        if (multicastManager != null) {
            multicastManager.pararEscuta();
        }
        
        if (gerenciadorEleicaoSocket != null) {
            gerenciadorEleicaoSocket.finalizar();
        }
    }
    
    /**
     * Implementação da interface SupercoordenadorCallback para comunicação intergrupos
     */
    private class SupercoordenadorCallbackImpl implements SupercoordenadorCallback {
        
        @Override
        public void definirNovoSupercoordenador(int grupoSupercoordenador, String processoSupercoordenador) {
            if (grupoSupercoordenador == 2 && processoSupercoordenador.equals("ProcessoRMI" + processoId)) {
                setSupercoordenador(true);
                System.out.println(TerminalColors.successMessage("[SUPERCOORDENADOR] ProcessoRMI" + processoId + " definido como SUPERCOORDENADOR GLOBAL!"));
            } else {
                setSupercoordenador(false);
                System.out.println(TerminalColors.warningMessage("[SUPERCOORDENADOR] Grupo " + grupoSupercoordenador + " - " + processoSupercoordenador + " é o supercoordenador"));
            }
        }
        
        @Override
        public String obterStatusGrupo() {
            StringBuilder sb = new StringBuilder();
            sb.append("Líder: ProcessoRMI").append(liderAtual);
            sb.append(", Processos ativos: ").append(processosAtivos);
            return sb.toString();
        }
        
        @Override
        public int getGrupoId() {
            return 2; // Grupo 2
        }
        
        @Override
        public boolean isLiderGrupo() {
            return liderAtual == processoId;
        }
        
        @Override
        public String obterInfoProcesso() {
            return "ProcessoRMI" + processoId + " (Parâmetro: " + parametroEleicao + ", Líder: " + isLiderGrupo() + ")";
        }
    }
}