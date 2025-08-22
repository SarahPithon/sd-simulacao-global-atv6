package br.com.utils;

import java.rmi.RemoteException;

/**
 * Interface para callback de supercoordenação entre grupos
 * Permite comunicação entre processos de diferentes grupos para
 * determinar e gerenciar o supercoordenador global
 */
public interface SupercoordenadorCallback {
    
    /**
     * Notifica que um novo supercoordenador foi definido
     * @param grupoSupercoordenador o grupo do supercoordenador (1 ou 2)
     * @param processoSupercoordenador o nome/ID do processo supercoordenador
     */
    void definirNovoSupercoordenador(int grupoSupercoordenador, String processoSupercoordenador) throws RemoteException;
    
    /**
     * Solicita informações de status do grupo
     * @return informações do status do grupo (líder, processos ativos, clock)
     */
    String obterStatusGrupo() throws RemoteException;
    
    /**
     * Obtém o ID do grupo (1 ou 2)
     * @return ID do grupo
     */
    int getGrupoId() throws RemoteException;
    
    /**
     * Verifica se este processo é líder do seu grupo
     * @return true se for líder, false caso contrário
     */
    boolean isLiderGrupo() throws RemoteException;
    
    /**
     * Obtém informações detalhadas do processo
     * @return string com informações do processo (ID, clock, status)
     */
    String obterInfoProcesso() throws RemoteException;
}