package br.com.grupo2;

import br.com.models.ConexaoOutrosProcessos;
import br.com.models.Mensagem;
import br.com.models.Processo;
import br.com.models.TipoMensagem;
import br.com.utils.TerminalColors;
import br.com.utils.GerenciadorMulticast;
import br.com.utils.LiderCallback;
import br.com.utils.SnapshotManager;
// import br.com.grpc.HeartbeatManager; // Removido para usar apenas multicast

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * ProcessoRMI1 - Implementação do Processo1 usando RMI e algoritmo de eleição em anel
 */
public class ProcessoRMI1 extends ProcessoRMIBase {
    
    private static final int RMI_PORT = 1101;
    private static final String RMI_NAME = "ProcessoRMI1";
    // private static HeartbeatManager heartbeatManager; // Removido para usar apenas multicast
    private static final int GRPC_PORT = 9011; // Porta gRPC para ProcessoRMI1
    
    private Processo processo;
    private Scanner scanner;
    private List<Mensagem> mensagensRecebidas;
    private List<Mensagem> mensagensEnviadas;
    private GerenciadorMulticast gerenciadorMulticast;
    private SnapshotManager snapshotManager;
    
    public ProcessoRMI1() throws RemoteException {
        super(1); // ID do processo = 1
        // ProcessoRMI3 é o líder inicial
        this.liderAtual = 3;
        this.scanner = new Scanner(System.in);
        this.mensagensRecebidas = new ArrayList<>();
        this.mensagensEnviadas = new ArrayList<>();
        
        // Inicializa o processo com informações específicas do ProcessoRMI1
        this.processo = new Processo(UUID.randomUUID(), "ProcessoRMI1", 0, 10, RMI_PORT, false, "ProcessoRMI3");
        
        // Inicializar HeartbeatManager (não como líder)
        // inicializarHeartbeatManager(); // Removido
        
        // Inicializar gerenciador multicast
        try {
            this.gerenciadorMulticast = new GerenciadorMulticast(this, 50002); // Porta específica do grupo 2
            this.gerenciadorMulticast.iniciarEscuta("ProcessoRMI1");
            System.out.println(TerminalColors.GREEN + "[MULTICAST] Gerenciador multicast iniciado para ProcessoRMI1" + TerminalColors.RESET);
        } catch (Exception e) {
            System.err.println("Erro ao inicializar multicast: " + e.getMessage());
        }
        
        // Configurar próximo processo no anel (ProcessoRMI1 -> ProcessoRMI2) com delay
        // Aguarda um pouco para que outros processos possam estar disponíveis
        scheduler.schedule(() -> {
            configurarProximoProcessoAutomatico();
        }, 3, TimeUnit.SECONDS);
        
        // Configurar conexões com outros processos
        ConexaoOutrosProcessos conexaoProcesso2 = new ConexaoOutrosProcessos();
        conexaoProcesso2.setNomeProcesso("ProcessoRMI2");
        conexaoProcesso2.setParametroEleicao(20);
        conexaoProcesso2.setPorta(1102);
        processo.getConexaoOutrosProcessos().add(conexaoProcesso2);
        
        ConexaoOutrosProcessos conexaoProcesso3 = new ConexaoOutrosProcessos();
        conexaoProcesso3.setNomeProcesso("ProcessoRMI3");
        conexaoProcesso3.setParametroEleicao(30);
        conexaoProcesso3.setPorta(1103);
        processo.getConexaoOutrosProcessos().add(conexaoProcesso3);
    }
    
    @Override
    protected void processarMensagemEspecifica(Mensagem mensagem) {
        // Atualiza o clock lógico
        processo.setClockProcesso(Math.max(processo.getClockProcesso(), mensagem.getClockMensagem()) + 1);
        
        // Adiciona à lista de mensagens recebidas
        mensagensRecebidas.add(mensagem);
        
        switch (mensagem.getTipoMensagem()) {
             case INTERACAO:
                 System.out.println(TerminalColors.CYAN + "[INTERAÇÃO] Mensagem recebida de " + 
                                   mensagem.getProcessoRemetente() + ": " + mensagem.getConteudoMensagem() + TerminalColors.RESET);
                 break;
             case PROCESSO_FALHOU:
                 String processoFalhou = mensagem.getConteudoMensagem();
                System.out.println(TerminalColors.RED + "[NOTIFICAÇÃO] Processo falhou: " + processoFalhou + TerminalColors.RESET);
                // Remove conexão do processo que falhou
                processo.getConexaoOutrosProcessos().removeIf(c -> c.getNomeProcesso().equals(processoFalhou));
                break;
            default:
                 System.out.println(TerminalColors.YELLOW + "[INFO] Mensagem de tipo " + mensagem.getTipoMensagem() + 
                                   " recebida" + TerminalColors.RESET);
                break;
        }
    }
    
    public void iniciarMenu() {
        System.out.println(TerminalColors.GREEN + "[SISTEMA] ProcessoRMI1 iniciado com sucesso!" + TerminalColors.RESET);
        
        while (estaAtivo) {
            exibirMenu();
        }
    }
    
    private void exibirMenu() {
        System.out.println("\n" + TerminalColors.GREEN + "=== ProcessoRMI1 - Menu ===" + TerminalColors.RESET);
        System.out.println("1. Enviar mensagem");
        // Só mostra opção de multicast se for o líder
        if (liderAtual == processoId) {
            System.out.println("2. Enviar mensagem via Multicast");
        }
        System.out.println("3. Exibir mensagens recebidas");
        System.out.println("4. Exibir mensagens enviadas");
        System.out.println("5. Exibir status do processo");
        // Só mostra opção de print geral se for supercoordenador
        if (isSupercoordenador()) {
            System.out.println("6. [SUPERCOORDENADOR] Print geral do sistema");
        }
        System.out.println("0. Sair");
        System.out.print("Escolha uma opção: ");
        
        try {
            int opcao = scanner.nextInt();
            scanner.nextLine();
            
            switch (opcao) {
                case 1:
                    enviarMensagemRMI();
                    break;
                case 2:
                    enviarMensagemMulticast();
                    break;
                case 3:
                    exibirMensagensRecebidas();
                    break;
                case 4:
                    exibirMensagensEnviadas();
                    break;
                case 5:
                    exibirStatusProcesso();
                    break;
                case 6:
                    if (isSupercoordenador()) {
                        executarPrintGeral();
                    } else {
                        System.out.println(TerminalColors.RED + "Apenas o supercoordenador pode executar esta opção!" + TerminalColors.RESET);
                    }
                    break;
                case 0:
                    System.out.println(TerminalColors.YELLOW + "Encerrando ProcessoRMI1..." + TerminalColors.RESET);
                    finalizar();
                    System.exit(0);
                    break;
                default:
                    System.out.println(TerminalColors.RED + "Opção inválida!" + TerminalColors.RESET);
            }
        } catch (Exception e) {
            System.err.println("Erro no menu: " + e.getMessage());
            scanner.nextLine(); // Limpa o buffer
        }
    }
    
    private void enviarMensagemRMI() {
        System.out.println("\n=== Enviar Mensagem RMI ===");
        System.out.println("Processos disponíveis:");
        System.out.println("1. ProcessoRMI2");
        System.out.println("2. ProcessoRMI3");
        
        System.out.print("Escolha o processo destinatário (número): ");
        int escolha = scanner.nextInt();
        scanner.nextLine();
        
        if (escolha < 1 || escolha > 2) {
            System.out.println(TerminalColors.RED + "Escolha inválida!" + TerminalColors.RESET);
            return;
        }
        
        String nomeProcessoDestinatario = (escolha == 1) ? "ProcessoRMI2" : "ProcessoRMI3";
        String rmiUrl = "rmi://localhost:" + (1100 + escolha + 1) + "/" + nomeProcessoDestinatario;
        
        System.out.print("Digite a mensagem: ");
        String conteudo = scanner.nextLine();
        
        try {
            ProcessoRMIInterface processoDestino = (ProcessoRMIInterface) Naming.lookup(rmiUrl);
            
            // Incrementa o clock lógico
            processo.setClockProcesso(processo.getClockProcesso() + 1);
            
            Mensagem mensagem = new Mensagem();
            mensagem.setIdMensagem(UUID.randomUUID());
            mensagem.setProcessoRemetente(String.valueOf(processoId));
             mensagem.setProcessoDestinatario(String.valueOf(escolha + 1));
             mensagem.setConteudoMensagem(conteudo);
             mensagem.setTipoMensagem(TipoMensagem.INTERACAO);
             mensagem.setClockMensagem(processo.getClockProcesso());
             mensagem.setDataMensagem(LocalDateTime.now());
            
            processoDestino.receberMensagem(mensagem);
            mensagensEnviadas.add(mensagem);
            
            System.out.println(TerminalColors.GREEN + "[SUCESSO] Mensagem enviada para " + 
                              nomeProcessoDestinatario + TerminalColors.RESET);
            
        } catch (Exception e) {
            System.err.println("Erro ao enviar mensagem RMI: " + e.getMessage());
            System.out.println(TerminalColors.RED + "[FALHA] Processo " + nomeProcessoDestinatario + 
                              " pode estar inativo" + TerminalColors.RESET);
            
            // Se a falha foi com o líder atual, detectar falha e iniciar eleição
            int processoDestinatarioId = escolha + 1;
            if (processoDestinatarioId == liderAtual) {
                System.out.println(TerminalColors.YELLOW + "[ELEIÇÃO] Líder inativo detectado! Iniciando eleição em anel..." + TerminalColors.RESET);
                iniciarEleicaoViaSocket();
            }
        }
    }
    
    private void enviarMensagemMulticast() {
        System.out.println("\n=== Enviar Mensagem via Multicast ===");
        System.out.print("Digite a mensagem: ");
        String conteudo = scanner.nextLine();
        
        try {
            // Verifica se é líder
            boolean isLider = (liderAtual == processoId);
            
            if (gerenciadorMulticast != null) {
                // Enviar mensagem via multicast usando o gerenciador
                gerenciadorMulticast.enviarMensagemComoLider(conteudo, "ProcessoRMI1", isLider);
                
                if (isLider) {
                    // Incrementa o clock lógico apenas se enviou a mensagem
                    processo.setClockProcesso(processo.getClockProcesso() + 1);
                    
                    // Criar mensagem para histórico local
                    Mensagem mensagem = new Mensagem();
                    mensagem.setIdMensagem(UUID.randomUUID());
                    mensagem.setProcessoRemetente("ProcessoRMI1");
                    mensagem.setProcessoDestinatario("TODOS");
                    mensagem.setConteudoMensagem(conteudo);
                    mensagem.setTipoMensagem(TipoMensagem.MULTICAST);
                    mensagem.setClockMensagem(processo.getClockProcesso());
                    mensagem.setDataMensagem(LocalDateTime.now());
                    
                    mensagensEnviadas.add(mensagem);
                }
            } else {
                System.out.println(TerminalColors.errorMessage("[MULTICAST] Gerenciador multicast não disponível."));
            }
            
        } catch (Exception e) {
            System.err.println(TerminalColors.errorMessage("[MULTICAST] Erro ao enviar mensagem: " + e.getMessage()));
        }
    }
    
    private void iniciarEleicaoAnel() {
        try {
            System.out.println(TerminalColors.BLUE + "[ELEIÇÃO] Iniciando eleição em anel via socket..." + TerminalColors.RESET);
            iniciarEleicaoViaSocket();
        } catch (Exception e) {
            System.err.println("Erro ao iniciar eleição via socket: " + e.getMessage());
            // Fallback para eleição RMI se socket falhar
            try {
                System.out.println(TerminalColors.YELLOW + "[ELEIÇÃO] Tentando eleição via RMI como fallback..." + TerminalColors.RESET);
                List<Integer> candidatos = new ArrayList<>();
                candidatos.add(processoId);
                iniciarEleicao(processoId, candidatos);
            } catch (RemoteException re) {
                System.err.println("Erro ao iniciar eleição via RMI: " + re.getMessage());
            }
        }
    }
    
    private void exibirMensagensRecebidas() {
        System.out.println("\n=== Mensagens Recebidas ===");
        if (mensagensRecebidas.isEmpty()) {
            System.out.println("Nenhuma mensagem recebida.");
        } else {
            for (Mensagem msg : mensagensRecebidas) {
                System.out.println(TerminalColors.CYAN + "[" + msg.getDataMensagem() + "] De: " + 
                                   msg.getProcessoRemetente() + " - " + msg.getConteudoMensagem() + TerminalColors.RESET);
            }
        }
    }
    
    private void exibirMensagensEnviadas() {
        System.out.println("\n=== Mensagens Enviadas ===");
        if (mensagensEnviadas.isEmpty()) {
            System.out.println("Nenhuma mensagem enviada.");
        } else {
            for (Mensagem msg : mensagensEnviadas) {
                System.out.println(TerminalColors.YELLOW + "[" + msg.getDataMensagem() + "] Para: " + 
                                   msg.getProcessoDestinatario() + " - " + msg.getConteudoMensagem() + TerminalColors.RESET);
            }
        }
    }
    
    private void exibirStatusProcesso() {
        System.out.println("\n=== Status do Processo ===");
        System.out.println("Nome: " + processo.getNomeProcesso());
        System.out.println("ID: " + processoId);
        System.out.println("Porta RMI: " + RMI_PORT);
        System.out.println("Clock Lógico: " + processo.getClockProcesso());
        System.out.println("Líder Atual: " + (liderAtual == -1 ? "Nenhum" : "ProcessoRMI" + liderAtual));
        System.out.println("Supercoordenador: " + (isSupercoordenador() ? "SIM" : "NÃO"));
        System.out.println("Eleição em Andamento: " + (eleicaoEmAndamento ? "Sim" : "Não"));
        System.out.println("Status: " + (estaAtivo ? "Ativo" : "Inativo"));
        System.out.println("Próximo no Anel: " + (proximoProcesso != null ? "Configurado" : "Não configurado"));
    }
    
    private void executarPrintGeral() {
        if (!isSupercoordenador()) {
            System.out.println(TerminalColors.RED + "Apenas o supercoordenador pode executar esta função!" + TerminalColors.RESET);
            return;
        }
        
        System.out.println(TerminalColors.CYAN + "\n=== PRINT GERAL DO SISTEMA ===" + TerminalColors.RESET);
        
        if (gerenciadorIntergrupos != null) {
            try {
                String statusGeral = gerenciadorIntergrupos.obterPrintGeral();
                System.out.println(statusGeral);
            } catch (Exception e) {
                System.err.println("Erro ao obter status geral: " + e.getMessage());
            }
        } else {
            System.out.println(TerminalColors.RED + "Gerenciador de comunicação intergrupos não inicializado!" + TerminalColors.RESET);
        }
    }
    
    private void configurarProximoProcesso() {
        System.out.println("\n=== Configurar Próximo Processo no Anel ===");
        System.out.println("1. ProcessoRMI2");
        System.out.println("2. ProcessoRMI3");
        
        System.out.print("Escolha o próximo processo (número): ");
        int escolha = scanner.nextInt();
        scanner.nextLine();
        
        if (escolha < 1 || escolha > 2) {
            System.out.println(TerminalColors.RED + "Escolha inválida!" + TerminalColors.RESET);
            return;
        }
        
        String nomeProcesso = (escolha == 1) ? "ProcessoRMI2" : "ProcessoRMI3";
        String rmiUrl = "rmi://localhost:" + (1100 + escolha + 1) + "/" + nomeProcesso;
        
        try {
            // Define timeout para conexões RMI (10 segundos)
            System.setProperty("sun.rmi.transport.tcp.responseTimeout", "10000");
            System.setProperty("sun.rmi.transport.connectionTimeout", "10000");
            
            ProcessoRMIInterface proximoProc = (ProcessoRMIInterface) Naming.lookup(rmiUrl);
            setProximoProcesso(proximoProc);
            System.out.println(TerminalColors.GREEN + "[SUCESSO] Próximo processo configurado: " + 
                              nomeProcesso + TerminalColors.RESET);
        } catch (Exception e) {
            System.err.println("Erro ao configurar próximo processo: " + e.getMessage());
        }
    }
    
    /**
     * Configura automaticamente o próximo processo no anel
     * ProcessoRMI1 -> ProcessoRMI2
     */
    private void configurarProximoProcessoAutomatico() {
        try {
            // Define timeout para conexões RMI (10 segundos)
            System.setProperty("sun.rmi.transport.tcp.responseTimeout", "10000");
            System.setProperty("sun.rmi.transport.connectionTimeout", "10000");
            
            String rmiUrl = "rmi://localhost:1102/ProcessoRMI2";
            ProcessoRMIInterface proximoProc = (ProcessoRMIInterface) Naming.lookup(rmiUrl);
            setProximoProcesso(proximoProc);
            System.out.println(TerminalColors.GREEN + "[ProcessoRMI1] Próximo processo configurado automaticamente: ProcessoRMI2" + TerminalColors.RESET);
        } catch (Exception e) {
            System.err.println(TerminalColors.YELLOW + "[ProcessoRMI1] Aviso: Não foi possível configurar próximo processo automaticamente: " + e.getMessage() + TerminalColors.RESET);
        }
    }
    
    // Método removido - usando apenas multicast diretamente
    
    public static void main(String[] args) {
        try {
            // Cria o registry RMI
            Registry registry = LocateRegistry.createRegistry(RMI_PORT);
            
            // Cria e registra o processo
            ProcessoRMI1 processo1 = new ProcessoRMI1();
            registry.rebind(RMI_NAME, processo1);
            
            System.out.println(TerminalColors.GREEN + "[SISTEMA] ProcessoRMI1 registrado no RMI registry na porta " + 
                              RMI_PORT + TerminalColors.RESET);
            
            // Inicializa o SnapshotManager
            processo1.snapshotManager = new SnapshotManager("ProcessoRMI1", () -> "ProcessoRMI1 - Clock: " + processo1.processo.getClockProcesso());
            processo1.snapshotManager.startSnapshotServer();
            
            // Inicia o menu interativo
            processo1.iniciarMenu();
            
        } catch (Exception e) {
            System.err.println("Erro ao iniciar ProcessoRMI1: " + e.getMessage());
            e.printStackTrace();
        }
    }
}