package br.com.utils;

/**
 * Interface para callback de liderança
 * Permite que o GerenciadorMulticast se comunique com os processos
 * para definir novos líderes quando mensagens NOVO_LIDER são recebidas
 */
public interface LiderCallback {
    
    /**
     * Define um novo líder
     * @param novoLider ID do novo líder
     */
    void definirNovoLider(int novoLider);
    
    /**
     * Obtém o ID do processo atual
     * @return ID do processo
     * @throws java.rmi.RemoteException se houver erro de comunicação RMI
     */
    int getProcessoId() throws java.rmi.RemoteException;
}