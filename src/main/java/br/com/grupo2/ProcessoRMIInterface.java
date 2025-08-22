package br.com.grupo2;

import br.com.models.Mensagem;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interface RMI para comunicação entre processos do grupo2
 * Utiliza algoritmo de eleição em anel
 */
public interface ProcessoRMIInterface extends Remote {
    
    /**
     * Recebe uma mensagem de outro processo
     * @param mensagem A mensagem a ser processada
     * @throws RemoteException Em caso de erro na comunicação RMI
     */
    void receberMensagem(Mensagem mensagem) throws RemoteException;
    
    /**
     * Inicia uma eleição no anel
     * @param processoIniciador ID do processo que iniciou a eleição
     * @param candidatos Lista de candidatos na eleição
     * @throws RemoteException Em caso de erro na comunicação RMI
     */
    void iniciarEleicao(int processoIniciador, List<Integer> candidatos) throws RemoteException;
    
    /**
     * Propaga a eleição no anel
     * @param candidatos Lista atual de candidatos
     * @throws RemoteException Em caso de erro na comunicação RMI
     */
    void propagarEleicao(List<Integer> candidatos) throws RemoteException;
    
    /**
     * Propaga a eleição no anel com parâmetros de eleição
     * @param iniciador ID do processo que iniciou a eleição
     * @param parametroRecebido Parâmetro de eleição recebido
     * @throws RemoteException Em caso de erro na comunicação RMI
     */
    void propagarEleicaoComParametro(int iniciador, int parametroRecebido) throws RemoteException;
    
    /**
     * Anuncia o novo líder eleito
     * @param novoLider ID do novo líder
     * @throws RemoteException Em caso de erro na comunicação RMI
     */
    void anunciarLider(int novoLider) throws RemoteException;
    
    /**
     * Verifica se o processo está ativo (heartbeat)
     * @return true se o processo está ativo
     * @throws RemoteException Em caso de erro na comunicação RMI
     */
    boolean estaAtivo() throws RemoteException;
    
    /**
     * Obtém o ID do processo
     * @return ID do processo
     * @throws RemoteException Em caso de erro na comunicação RMI
     */
    int getProcessoId() throws RemoteException;
    
    /**
     * Obtém o ID do líder atual conhecido por este processo
     * @return ID do líder atual
     * @throws RemoteException Em caso de erro na comunicação RMI
     */
    int getLiderAtual() throws RemoteException;
    
    /**
     * Define o próximo processo no anel
     * @param proximoProcesso Referência RMI para o próximo processo
     * @throws RemoteException Em caso de erro na comunicação RMI
     */
    void setProximoProcesso(ProcessoRMIInterface proximoProcesso) throws RemoteException;
    
    /**
     * Envia heartbeat para o próximo processo no anel
     * @throws RemoteException Em caso de erro na comunicação RMI
     */
    void enviarHeartbeat() throws RemoteException;
    
    /**
     * Recebe heartbeat de outro processo
     * @param remetenteId ID do processo remetente
     * @throws RemoteException Em caso de erro na comunicação RMI
     */
    void receberHeartbeat(int remetenteId) throws RemoteException;
}