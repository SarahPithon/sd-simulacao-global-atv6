package br.com.grupo1;

import br.com.models.ConexaoOutrosProcessos;
import br.com.models.Mensagem;
import br.com.models.Processo;
import br.com.models.TipoMensagem;
import br.com.utils.TerminalColors;
import br.com.utils.SupercoordenadorCallback;
import br.com.utils.GerenciadorComunicacaoIntergrupos;
import br.com.utils.GerenciadorMulticast;
import br.com.utils.SnapshotManager;
import br.com.grpc.HeartbeatManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class Processo3 {

    private static Processo processo;
    private static Scanner scanner = new Scanner(System.in);
    private static HeartbeatManager heartbeatManager;
    private static GerenciadorMulticast gerenciadorMulticast;
    private static SnapshotManager snapshotManager;
    private static final int GRPC_PORT = 9013;
    
    private static GerenciadorComunicacaoIntergrupos gerenciadorIntergrupos;
    private static SupercoordenadorCallbackImpl callbackImpl;
    
    private static String liderAtual = "Processo3";
    private static boolean eleicaoEmAndamento = false;
    private static final int TIMEOUT_ELEICAO = 3000;

    public static void main(String[] args) {
        processo = new Processo(UUID.randomUUID(), "Processo3", 0, 30, 50053, false, null);
        processo.setSupercoordenador(true);


        ConexaoOutrosProcessos conexaoProcesso1 = new ConexaoOutrosProcessos();
        conexaoProcesso1.setNomeProcesso("Processo1");
        conexaoProcesso1.setParametroEleicao(10);
        conexaoProcesso1.setPorta(50051);
        processo.getConexaoOutrosProcessos().add(conexaoProcesso1);

        ConexaoOutrosProcessos conexaoProcesso2 = new ConexaoOutrosProcessos();
        conexaoProcesso2.setNomeProcesso("Processo2");
        conexaoProcesso2.setParametroEleicao(20);
        conexaoProcesso2.setPorta(50052);
        processo.getConexaoOutrosProcessos().add(conexaoProcesso2);
        
        callbackImpl = new SupercoordenadorCallbackImpl();
        gerenciadorIntergrupos = new GerenciadorComunicacaoIntergrupos(1, processo.getNomeProcesso(), callbackImpl);
        gerenciadorIntergrupos.iniciar();
        

        try {
            gerenciadorMulticast = new GerenciadorMulticast(50001);
            gerenciadorMulticast.iniciarEscuta(processo.getNomeProcesso());
            System.out.println(TerminalColors.multicastMessage("[MULTICAST] Multicast manager iniciado e escutando"));
        } catch (IOException e) {
            System.err.println(TerminalColors.errorMessage("[ERRO] Erro ao iniciar multicast: " + e.getMessage()));
        }


        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(processo.getPorta())) {
                System.out.println(TerminalColors.successMessage("[SERVIDOR] " + processo.getNomeProcesso() + " iniciado na porta " + processo.getPorta()));
                
                while (true) {
                    Socket socket = serverSocket.accept();

                    new Thread(() -> {
                        try {
                            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

                            while (true) {
                                Mensagem mensagem = (Mensagem) in.readObject();

                                processo.setClockProcesso(Math.max(processo.getClockProcesso(), mensagem.getClockMensagem()) + 1);

                                if (mensagem.getTipoMensagem().equals(TipoMensagem.ELEICAO)) {
                                    System.out.println(TerminalColors.multicastMessage("\n[ELEIÇÃO] Mensagem de eleição recebida de: " + mensagem.getProcessoRemetente()));
                                    processarMensagemEleicao(mensagem, out);
                                } else if (mensagem.getTipoMensagem().equals(TipoMensagem.LIDER_ELEITO)) {
                                    System.out.println(TerminalColors.multicastMessage("\n[ELEIÇÃO] Novo líder eleito: " + mensagem.getProcessoRemetente()));
                                    
                                    if (liderAtual.equals(processo.getNomeProcesso()) && !mensagem.getProcessoRemetente().equals(processo.getNomeProcesso())) {
                                        pararHeartbeat();
                                    }
                                    
                                    liderAtual = mensagem.getProcessoRemetente();
                                    eleicaoEmAndamento = false;
                                } else if (mensagem.getTipoMensagem().equals(TipoMensagem.ELEICAO_OK)) {
                                    System.out.println(TerminalColors.multicastMessage("\n[ELEIÇÃO] Resposta OK recebida de: " + mensagem.getProcessoRemetente()));
                                } else if (mensagem.getTipoMensagem().equals(TipoMensagem.NOVO_LIDER)) {
                                    liderAtual = mensagem.getProcessoRemetente();
                                    eleicaoEmAndamento = false;
                                    System.out.println(TerminalColors.successMessage("\n[ELEIÇÃO] " + mensagem.getProcessoRemetente() + " foi eleito como NOVO LÍDER!"));
                                    System.out.println(TerminalColors.successMessage("[ELEIÇÃO] Líder atual atualizado para: " + liderAtual));
                                } else if (mensagem.getTipoMensagem().equals(TipoMensagem.PROCESSO_FALHOU)) {
                                    String processoFalhou = mensagem.getConteudoMensagem();
                                    System.out.println(TerminalColors.warningMessage("\n[NOTIFICAÇÃO] Processo falhou: " + processoFalhou));
                                    processo.getConexaoOutrosProcessos().removeIf(c -> c.getNomeProcesso().equals(processoFalhou));
                                } else if (mensagem.getTipoMensagem().equals(TipoMensagem.INTERACAO)) {
                                    processo.getMensagensRecebidas().add(mensagem);

                                    System.out.println("\n" + TerminalColors.multicastMessage("====================================="));
                                    System.out.println(TerminalColors.multicastMessage("Mensagem Recebida no " + processo.getNomeProcesso() + ":"));
                                    System.out.println(TerminalColors.multicastMessage("CLOCK: " + mensagem.getClockMensagem()));
                                    System.out.println(TerminalColors.multicastMessage("Remetente: " + mensagem.getProcessoRemetente()));
                                    System.out.println(TerminalColors.multicastMessage("Conteudo: " + mensagem.getConteudoMensagem()));
                                    System.out.println(TerminalColors.multicastMessage("====================================="));

                                    String resposta = "Mensagem recebida com sucesso";
                                    
                                    processo.setClockProcesso(processo.getClockProcesso() + 1);
                                    
                                    Mensagem respostaMensagem = new Mensagem();
                                    respostaMensagem.setConteudoMensagem(resposta);
                                    respostaMensagem.setProcessoRemetente(processo.getNomeProcesso());
                                    respostaMensagem.setProcessoDestinatario(mensagem.getProcessoRemetente());
                                    respostaMensagem.setTipoMensagem(TipoMensagem.INTERACAO);
                                    respostaMensagem.setClockMensagem(processo.getClockProcesso());
                                    respostaMensagem.setDataMensagem(LocalDateTime.now());
                                    respostaMensagem.setIdMensagem(UUID.randomUUID());
                                    
                                    out.writeObject(respostaMensagem);
                                    out.flush();
                                    
                                    processo.getMensagensEnviadas().add(respostaMensagem);
                                }
                            }
                        } catch (IOException | ClassNotFoundException e) {
                            System.err.println(TerminalColors.errorMessage("[ERRO] Erro na comunicação: " + e.getMessage()));
                        }
                    }).start();
                }
            } catch (IOException e) {
                System.err.println(TerminalColors.errorMessage("[ERRO] Erro ao iniciar servidor: " + e.getMessage()));
            }
        }).start();

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }


        for (ConexaoOutrosProcessos conexao : processo.getConexaoOutrosProcessos()) {
            new Thread(() -> {
                long inicioTentativa = System.currentTimeMillis();
                while (true) {
                    try {
                        Socket socket = new Socket("localhost", conexao.getPorta());

                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());

                        conexao.setObjectOutputStream(objectOutputStream);
                        conexao.setObjectInputStream(objectInputStream);

                        System.out.println(TerminalColors.successMessage("Conexão estabelecida com: " + conexao.getNomeProcesso()));
                        break;
                    } catch (IOException e) {
                        if (System.currentTimeMillis() - inicioTentativa >= 10000) {
                            System.err.println(TerminalColors.errorMessage("Não foi possível conectar ao " + conexao.getNomeProcesso()));
                            break;
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }).start();
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        inicializarHeartbeatManager();
        

        snapshotManager = new SnapshotManager(processo.getNomeProcesso(), () -> {
            return processo.getNomeProcesso() + " - Clock: " + processo.getClockProcesso();
        });
        snapshotManager.startSnapshotServer();
        
        if (liderAtual.equals(processo.getNomeProcesso())) {
            System.out.println(TerminalColors.successMessage("[LIDERANÇA] " + processo.getNomeProcesso() + " é o líder inicial - iniciando heartbeat"));
            iniciarHeartbeatComoLider();
        }

        exibirMenu();
    }

    private static void exibirMenu() {
        while (true) {
            System.out.println("\n" + TerminalColors.successMessage("=== " + processo.getNomeProcesso() + " - Menu ==="));
            System.out.println("1. Enviar mensagem");
            System.out.println("2. Enviar mensagem via Multicast");
            System.out.println("3. Exibir mensagens recebidas");
            System.out.println("4. Exibir mensagens enviadas");
            System.out.println("5. Exibir status do processo");
            
            if (processo.isSupercoordenador()) {
                System.out.println("6. [SUPERCOORDENADOR] Print geral do sistema");
            }
            
            System.out.println("0. Sair");
            System.out.print("Escolha uma opção: ");
            
            int opcao = scanner.nextInt();
            scanner.nextLine();

            switch (opcao) {
                case 1:
                    enviarMensagem();
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
                    if (processo.isSupercoordenador()) {
                        executarPrintGeral();
                    } else {
                        System.out.println(TerminalColors.errorMessage("Apenas o supercoordenador pode executar esta opção!"));
                    }
                    break;
                case 0:
                    System.out.println(TerminalColors.warningMessage("Encerrando " + processo.getNomeProcesso() + "..."));
                    System.exit(0);
                    break;
                default:
                    System.out.println(TerminalColors.errorMessage("Opção inválida!"));
            }
        }
    }

    private static void enviarMensagem() {
        System.out.println("\n=== Enviar Mensagem ===");
        System.out.println("Processos disponíveis:");
        
        System.out.println("1. Processo1");
        System.out.println("2. Processo2");
        
        System.out.print("Escolha o processo destinatário (número): ");
        int escolha = scanner.nextInt();
        scanner.nextLine();
        
        if (escolha < 1 || escolha > 2) {
            System.out.println(TerminalColors.errorMessage("Escolha inválida!"));
            return;
        }
        
        String nomeProcessoDestinatario = (escolha == 1) ? "Processo1" : "Processo2";
        
        ConexaoOutrosProcessos conexaoEscolhida = null;
        for (ConexaoOutrosProcessos conexao : processo.getConexaoOutrosProcessos()) {
            if (conexao.getNomeProcesso().equals(nomeProcessoDestinatario)) {
                conexaoEscolhida = conexao;
                break;
            }
        }
        
        System.out.print("Digite a mensagem: ");
        String conteudo = scanner.nextLine();
        
        if (conexaoEscolhida == null || conexaoEscolhida.getObjectOutputStream() == null) {
            System.out.println(TerminalColors.warningMessage("Processo " + nomeProcessoDestinatario + " está inativo."));
            
            if (nomeProcessoDestinatario.equals(liderAtual)) {
                System.out.println(TerminalColors.errorMessage("[FALHA] O líder " + liderAtual + " falhou! Iniciando eleição..."));
                new Thread(() -> iniciarEleicao()).start();
            } else {
                System.out.println(TerminalColors.warningMessage("[FALHA] Processo " + nomeProcessoDestinatario + " falhou. Notificando outros processos..."));
                notificarFalhaProcesso(nomeProcessoDestinatario);
            }
            return;
        }
        
        try {
            processo.setClockProcesso(processo.getClockProcesso() + 1);
            
            Mensagem mensagem = new Mensagem();
            mensagem.setIdMensagem(UUID.randomUUID());
            mensagem.setProcessoRemetente(processo.getNomeProcesso());
            mensagem.setProcessoDestinatario(conexaoEscolhida.getNomeProcesso());
            mensagem.setDataMensagem(LocalDateTime.now());
            mensagem.setClockMensagem(processo.getClockProcesso());
            mensagem.setConteudoMensagem(conteudo);
            mensagem.setTipoMensagem(TipoMensagem.INTERACAO);
            
            conexaoEscolhida.getObjectOutputStream().writeObject(mensagem);
            conexaoEscolhida.getObjectOutputStream().flush();
            
            processo.getMensagensEnviadas().add(mensagem);
            
            System.out.println(TerminalColors.successMessage("Mensagem enviada para " + conexaoEscolhida.getNomeProcesso() + "!"));
            
            try {
                Mensagem resposta = (Mensagem) conexaoEscolhida.getObjectInputStream().readObject();
                processo.setClockProcesso(Math.max(processo.getClockProcesso(), resposta.getClockMensagem()) + 1);
                processo.getMensagensRecebidas().add(resposta);
                System.out.println(TerminalColors.multicastMessage("Resposta recebida: " + resposta.getConteudoMensagem()));
            } catch (Exception e) {
                System.out.println(TerminalColors.warningMessage("Não foi possível receber resposta do " + conexaoEscolhida.getNomeProcesso()));
                
                if (nomeProcessoDestinatario.equals(liderAtual)) {
                    System.out.println(TerminalColors.errorMessage("[FALHA] O líder " + liderAtual + " falhou! Iniciando eleição..."));
                    new Thread(() -> iniciarEleicao()).start();
                } else {
                    System.out.println(TerminalColors.warningMessage("[FALHA] Processo " + nomeProcessoDestinatario + " falhou. Notificando outros processos..."));
                    notificarFalhaProcesso(nomeProcessoDestinatario);
                }
                
                processo.getConexaoOutrosProcessos().removeIf(c -> c.getNomeProcesso().equals(nomeProcessoDestinatario));
            }
            
        } catch (IOException e) {
            System.out.println(TerminalColors.warningMessage("Processo " + nomeProcessoDestinatario + " está inativo."));
            
            if (nomeProcessoDestinatario.equals(liderAtual)) {
                System.out.println(TerminalColors.errorMessage("[FALHA] O líder " + liderAtual + " falhou! Iniciando eleição..."));
                new Thread(() -> iniciarEleicao()).start();
            } else {
                System.out.println(TerminalColors.warningMessage("[FALHA] Processo " + nomeProcessoDestinatario + " falhou. Notificando outros processos..."));
                notificarFalhaProcesso(nomeProcessoDestinatario);
            }
        }
    }

    private static void enviarMensagemMulticast() {
        System.out.println("\n=== Enviar Mensagem via Multicast ===");
        System.out.print("Digite a mensagem: ");
        String mensagem = scanner.nextLine();
        
        try {
            boolean isLider = liderAtual.equals(processo.getNomeProcesso());
            
            if (heartbeatManager != null && heartbeatManager.getMulticastManager() != null) {
                heartbeatManager.getMulticastManager().enviarMensagemComoLider(mensagem, processo.getNomeProcesso(), isLider);
            } else {
                System.out.println(TerminalColors.errorMessage("[MULTICAST] Gerenciador multicast não disponível."));
            }
        } catch (Exception e) {
            System.err.println(TerminalColors.errorMessage("[MULTICAST] Erro ao enviar mensagem: " + e.getMessage()));
        }
    }

    private static void exibirMensagensRecebidas() {
        System.out.println("\n=== Mensagens Recebidas ===");
        if (processo.getMensagensRecebidas().isEmpty()) {
            System.out.println("Nenhuma mensagem recebida.");
        } else {
            for (Mensagem msg : processo.getMensagensRecebidas()) {
                System.out.println("De: " + msg.getProcessoRemetente() + " | Conteúdo: " + msg.getConteudoMensagem() + " | Clock: " + msg.getClockMensagem());
            }
        }
    }

    private static void exibirMensagensEnviadas() {
        System.out.println("\n=== Mensagens Enviadas ===");
        if (processo.getMensagensEnviadas().isEmpty()) {
            System.out.println("Nenhuma mensagem enviada.");
        } else {
            for (Mensagem msg : processo.getMensagensEnviadas()) {
                System.out.println("Para: " + msg.getProcessoDestinatario() + " | Conteúdo: " + msg.getConteudoMensagem() + " | Clock: " + msg.getClockMensagem());
            }
        }
    }

    private static void exibirStatusProcesso() {
        System.out.println("\n" + TerminalColors.successMessage("=== Status do " + processo.getNomeProcesso() + " ==="));
        System.out.println("ID: " + processo.getIdProcesso());
        System.out.println("Nome: " + processo.getNomeProcesso());
        System.out.println("Clock: " + processo.getClockProcesso());
        System.out.println("Parâmetro de eleição: " + processo.getParametroEleicao());
        System.out.println("Porta: " + processo.getPorta());
        System.out.println("É líder: " + processo.isLider());
        System.out.println("É supercoordenador: " + processo.isSupercoordenador());
        System.out.println("Líder atual: " + liderAtual);
        System.out.println("Eleição em andamento: " + eleicaoEmAndamento);
        System.out.println("Mensagens recebidas: " + processo.getMensagensRecebidas().size());
        System.out.println("Mensagens enviadas: " + processo.getMensagensEnviadas().size());
        System.out.println("Conexões ativas: " + processo.getConexaoOutrosProcessos().stream()
                .filter(c -> c.getObjectOutputStream() != null)
                .count());
    }
    
    private static void executarPrintGeral() {
        if (!processo.isSupercoordenador()) {
            System.out.println(TerminalColors.errorMessage("[ERRO] Apenas o supercoordenador pode executar esta função!"));
            return;
        }
        
        System.out.println(TerminalColors.warningMessage("\n[SUPERCOORDENADOR] Iniciando coleta de snapshot do sistema distribuído..."));
        
        String snapshotCompleto = SnapshotManager.collectSystemSnapshot();
        System.out.println(snapshotCompleto);
    }
    
    private static void processarMensagemEleicao(Mensagem mensagem, ObjectOutputStream out) {
        try {
            int parametroRemetente = getParametroEleicao(mensagem.getProcessoRemetente());
            int meuParametro = processo.getParametroEleicao();
            
            if (parametroRemetente < meuParametro) {
                processo.setClockProcesso(processo.getClockProcesso() + 1);
                
                Mensagem resposta = new Mensagem();
                resposta.setIdMensagem(UUID.randomUUID());
                resposta.setProcessoRemetente(processo.getNomeProcesso());
                resposta.setProcessoDestinatario(mensagem.getProcessoRemetente());
                resposta.setDataMensagem(LocalDateTime.now());
                resposta.setClockMensagem(processo.getClockProcesso());
                resposta.setConteudoMensagem("OK");
                resposta.setTipoMensagem(TipoMensagem.ELEICAO_OK);
                
                out.writeObject(resposta);
                out.flush();
                
                System.out.println(TerminalColors.multicastMessage("[ELEIÇÃO] Enviado OK para " + mensagem.getProcessoRemetente()));
                
                new Thread(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    iniciarEleicao();
                }).start();
            }
        } catch (IOException e) {
            System.err.println(TerminalColors.errorMessage("[ERRO] Erro ao processar mensagem de eleição: " + e.getMessage()));
        }
    }
    
    private static int getParametroEleicao(String nomeProcesso) {
        return processo.getConexaoOutrosProcessos().stream()
                .filter(p -> p.getNomeProcesso().equals(nomeProcesso))
                .findFirst()
                .map(ConexaoOutrosProcessos::getParametroEleicao)
                .orElse(0);
    }
    
    private static void iniciarEleicao() {
        synchronized (Processo3.class) {
            if (eleicaoEmAndamento) {
                return;
            }
            eleicaoEmAndamento = true;
        }
        
        System.out.println(TerminalColors.warningMessage("\n[ELEIÇÃO] " + processo.getNomeProcesso() + " detectou falha do líder " + liderAtual + " - INICIANDO ELEIÇÃO!"));
        System.out.println(TerminalColors.multicastMessage("[ELEIÇÃO] Enviando mensagens de eleição para processos com parâmetro maior..."));
        
        List<ConexaoOutrosProcessos> processosComParametroMaior = processo.getConexaoOutrosProcessos().stream()
                .filter(c -> c.getParametroEleicao() > processo.getParametroEleicao())
                .filter(c -> c.getObjectOutputStream() != null)
                .filter(c -> !c.getNomeProcesso().equals(liderAtual))
                .collect(Collectors.toList());
        
        if (processosComParametroMaior.isEmpty()) {
            System.out.println(TerminalColors.successMessage("[ELEIÇÃO] Nenhum processo com parâmetro maior encontrado - " + processo.getNomeProcesso() + " se tornará líder!"));
            tornarSeLider();
            return;
        }
        
        boolean mensagemEnviadaComSucesso = false;
        
        for (ConexaoOutrosProcessos conexao : processosComParametroMaior) {
            try {
                processo.setClockProcesso(processo.getClockProcesso() + 1);
                
                Mensagem mensagemEleicao = new Mensagem();
                mensagemEleicao.setIdMensagem(UUID.randomUUID());
                mensagemEleicao.setProcessoRemetente(processo.getNomeProcesso());
                mensagemEleicao.setProcessoDestinatario(conexao.getNomeProcesso());
                mensagemEleicao.setDataMensagem(LocalDateTime.now());
                mensagemEleicao.setClockMensagem(processo.getClockProcesso());
                mensagemEleicao.setConteudoMensagem("ELEICAO");
                mensagemEleicao.setTipoMensagem(TipoMensagem.ELEICAO);
                
                conexao.getObjectOutputStream().writeObject(mensagemEleicao);
                conexao.getObjectOutputStream().flush();
                
                System.out.println(TerminalColors.multicastMessage("[ELEIÇÃO] Mensagem de eleição enviada para " + conexao.getNomeProcesso()));
                mensagemEnviadaComSucesso = true;
            } catch (IOException e) {
                System.err.println(TerminalColors.errorMessage("[ELEIÇÃO] Erro ao enviar mensagem de eleição para " + conexao.getNomeProcesso()));
            }
        }
        
        try {
            Thread.sleep(TIMEOUT_ELEICAO);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (!mensagemEnviadaComSucesso) {
            System.out.println(TerminalColors.successMessage("[ELEIÇÃO] Nenhuma mensagem de eleição enviada com sucesso. " + processo.getNomeProcesso() + " se tornará líder!"));
            tornarSeLider();
        } 
    }
    
    private static void tornarSeLider() {
        liderAtual = processo.getNomeProcesso();
        eleicaoEmAndamento = false;
        processo.setLider(true);
        
        System.out.println(TerminalColors.successMessage("\n[ELEIÇÃO] " + processo.getNomeProcesso() + " SE TORNOU O NOVO LÍDER!"));
        
        processo.setSupercoordenador(true);
        System.out.println(TerminalColors.successMessage("[SUPERCOORDENADOR] " + processo.getNomeProcesso() + " definido como SUPERCOORDENADOR!"));
        
        if (gerenciadorIntergrupos != null) {
            gerenciadorIntergrupos.notificarNovoLider(processo.getNomeProcesso());
        }
        
        iniciarHeartbeatComoLider();
        
        System.out.println(TerminalColors.successMessage("[ELEIÇÃO] Enviando anúncio de liderança para todos os processos..."));
        
        // Anuncia liderança para todos os processos
        for (ConexaoOutrosProcessos conexao : processo.getConexaoOutrosProcessos()) {
            if (conexao.getObjectOutputStream() != null) {
                try {
                    processo.setClockProcesso(processo.getClockProcesso() + 1);
                    
                    Mensagem anuncio = new Mensagem();
                    anuncio.setIdMensagem(UUID.randomUUID());
                    anuncio.setProcessoRemetente(processo.getNomeProcesso());
                    anuncio.setProcessoDestinatario(conexao.getNomeProcesso());
                    anuncio.setDataMensagem(LocalDateTime.now());
                    anuncio.setClockMensagem(processo.getClockProcesso());
                    anuncio.setConteudoMensagem(processo.getNomeProcesso());
                    anuncio.setTipoMensagem(TipoMensagem.NOVO_LIDER);
                    
                    conexao.getObjectOutputStream().writeObject(anuncio);
                    conexao.getObjectOutputStream().flush();
                    
                    System.out.println(TerminalColors.successMessage("[ELEIÇÃO] Anúncio de liderança enviado para " + conexao.getNomeProcesso()));
                    
                    processo.getMensagensEnviadas().add(anuncio);
                } catch (IOException e) {
                    System.err.println(TerminalColors.errorMessage("[ELEIÇÃO] Erro ao anunciar liderança para " + conexao.getNomeProcesso()));
                }
            }
        }
        
        System.out.println(TerminalColors.successMessage("[ELEIÇÃO] Eleição finalizada - " + processo.getNomeProcesso() + " é o novo líder do sistema!"));
    }
    

    
    private static void enviarHeartbeat() {
        try {
            if (heartbeatManager == null) {
                List<ConexaoOutrosProcessos> outrosProcessos = processo.getConexaoOutrosProcessos();
                heartbeatManager = new HeartbeatManager(processo.getNomeProcesso(), GRPC_PORT, outrosProcessos);
                heartbeatManager.startServer();
            }
            
            if (!heartbeatManager.isLeader()) {
                heartbeatManager.startAsLeader();
                System.out.println(TerminalColors.successMessage("[HEARTBEAT] Iniciado como líder - monitorando outros processos"));
            }
        } catch (Exception e) {
            System.err.println(TerminalColors.errorMessage("[HEARTBEAT] Erro ao enviar heartbeat: " + e.getMessage()));
        }
    }
    
    private static void inicializarHeartbeatManager() {
        try {
            int grpcPort = GRPC_PORT;
            heartbeatManager = new HeartbeatManager(processo.getNomeProcesso(), grpcPort, processo.getConexaoOutrosProcessos());
            
            if (gerenciadorMulticast != null) {
                heartbeatManager.setMulticastManager(gerenciadorMulticast);
            }
            
            heartbeatManager.startServer();
            
            System.out.println(TerminalColors.successMessage("[HEARTBEAT] Servidor gRPC iniciado na porta " + grpcPort));
        } catch (Exception e) {
            System.err.println(TerminalColors.errorMessage("[HEARTBEAT] Erro ao inicializar HeartbeatManager: " + e.getMessage()));
        }
    }
    
    private static void iniciarHeartbeatComoLider() {
        try {
            if (heartbeatManager != null) {
                heartbeatManager.startAsLeader();
                System.out.println(TerminalColors.successMessage("[HEARTBEAT] Iniciado como líder - monitorando outros processos"));
            }
        } catch (Exception e) {
            System.err.println(TerminalColors.errorMessage("[HEARTBEAT] Erro ao iniciar heartbeat como líder: " + e.getMessage()));
        }
    }
    
    private static void pararHeartbeat() {
        try {
            if (heartbeatManager != null) {
                heartbeatManager.stop();
                System.out.println(TerminalColors.warningMessage("[HEARTBEAT] Heartbeat parado - não sou mais o líder"));
            }
        } catch (Exception e) {
            System.err.println(TerminalColors.errorMessage("[HEARTBEAT] Erro ao parar heartbeat: " + e.getMessage()));
        }
    }
    
    private static void notificarFalhaProcesso(String processoFalhou) {
        System.out.println(TerminalColors.warningMessage("\n[NOTIFICAÇÃO] Notificando falha do processo: " + processoFalhou));
        
        for (ConexaoOutrosProcessos conexao : processo.getConexaoOutrosProcessos()) {
            if (conexao.getObjectOutputStream() != null && !conexao.getNomeProcesso().equals(processoFalhou)) {
                try {
                    processo.setClockProcesso(processo.getClockProcesso() + 1);
                    
                    Mensagem notificacao = new Mensagem();
                    notificacao.setIdMensagem(UUID.randomUUID());
                    notificacao.setProcessoRemetente(processo.getNomeProcesso());
                    notificacao.setProcessoDestinatario(conexao.getNomeProcesso());
                    notificacao.setDataMensagem(LocalDateTime.now());
                    notificacao.setClockMensagem(processo.getClockProcesso());
                    notificacao.setConteudoMensagem(processoFalhou);
                    notificacao.setTipoMensagem(TipoMensagem.PROCESSO_FALHOU);
                    
                    conexao.getObjectOutputStream().writeObject(notificacao);
                    conexao.getObjectOutputStream().flush();
                    
                    System.out.println(TerminalColors.warningMessage("[NOTIFICAÇÃO] Falha notificada para " + conexao.getNomeProcesso()));
                } catch (IOException e) {
                    System.err.println(TerminalColors.errorMessage("[ERRO] Erro ao notificar falha para " + conexao.getNomeProcesso()));
                }
            }
        }
    }
    
    private static class SupercoordenadorCallbackImpl implements SupercoordenadorCallback {
        
        @Override
        public void definirNovoSupercoordenador(int grupoSupercoordenador, String processoSupercoordenador) {
            if (grupoSupercoordenador == 1 && processoSupercoordenador.equals(processo.getNomeProcesso())) {
                processo.setSupercoordenador(true);
                System.out.println(TerminalColors.successMessage("[SUPERCOORDENADOR] " + processo.getNomeProcesso() + " definido como SUPERCOORDENADOR GLOBAL!"));
            } else {
                processo.setSupercoordenador(false);
                System.out.println(TerminalColors.warningMessage("[SUPERCOORDENADOR] Grupo " + grupoSupercoordenador + " - " + processoSupercoordenador + " é o supercoordenador"));
            }
        }
        
        @Override
        public String obterStatusGrupo() {
            StringBuilder sb = new StringBuilder();
            sb.append("Líder: ").append(liderAtual);
            sb.append(", Clock: ").append(processo.getClockProcesso());
            sb.append(", Processos ativos: ");
            
            long processosAtivos = processo.getConexaoOutrosProcessos().stream()
                .filter(c -> c.getObjectOutputStream() != null)
                .count() + 1;
            
            sb.append(processosAtivos);
            return sb.toString();
        }
        
        @Override
        public int getGrupoId() {
            return 1;
        }
        
        @Override
        public boolean isLiderGrupo() {
            return liderAtual.equals(processo.getNomeProcesso());
        }
        
        @Override
        public String obterInfoProcesso() {
            return processo.getNomeProcesso() + " (Clock: " + processo.getClockProcesso() + ", Líder: " + isLiderGrupo() + ")";
        }
    }
}