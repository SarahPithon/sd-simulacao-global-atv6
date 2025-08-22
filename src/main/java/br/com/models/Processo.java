package br.com.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Processo {
    private UUID idProcesso;
    private String nomeProcesso;
    private int clockProcesso;
    private int parametroEleicao;
    private int porta;
    private boolean lider;
    private String liderAtual;
    private boolean supercoordenador = false; // Indica se este processo é o supercoordenador
    private List<ConexaoOutrosProcessos> conexaoOutrosProcessos;
    private List<Mensagem> mensagensRecebidas;
    private List<Mensagem> mensagensEnviadas;
    
    public Processo() {
        this.conexaoOutrosProcessos = new ArrayList<>();
        this.mensagensRecebidas = new ArrayList<>();
        this.mensagensEnviadas = new ArrayList<>();
    }
    
    public Processo(UUID idProcesso, String nomeProcesso, int clockProcesso, int parametroEleicao, int porta, boolean lider, String liderAtual) {
        this();
        this.idProcesso = idProcesso;
        this.nomeProcesso = nomeProcesso;
        this.clockProcesso = clockProcesso;
        this.parametroEleicao = parametroEleicao;
        this.porta = porta;
        this.lider = lider;
        this.liderAtual = liderAtual;
    }
    
    public UUID getIdProcesso() {
        return idProcesso;
    }
    
    public void setIdProcesso(UUID idProcesso) {
        this.idProcesso = idProcesso;
    }
    
    public String getNomeProcesso() {
        return nomeProcesso;
    }
    
    public void setNomeProcesso(String nomeProcesso) {
        this.nomeProcesso = nomeProcesso;
    }
    
    public int getClockProcesso() {
        return clockProcesso;
    }
    
    public void setClockProcesso(int clockProcesso) {
        this.clockProcesso = clockProcesso;
    }
    
    public int getParametroEleicao() {
        return parametroEleicao;
    }
    
    public void setParametroEleicao(int parametroEleicao) {
        this.parametroEleicao = parametroEleicao;
    }
    
    public int getPorta() {
        return porta;
    }
    
    public void setPorta(int porta) {
        this.porta = porta;
    }
    
    public boolean isLider() {
        return lider;
    }
    
    public void setLider(boolean lider) {
        this.lider = lider;
    }
    
    public String getLiderAtual() {
        return liderAtual;
    }
    
    public void setLiderAtual(String liderAtual) {
        this.liderAtual = liderAtual;
    }
    
    public List<ConexaoOutrosProcessos> getConexaoOutrosProcessos() {
        return conexaoOutrosProcessos;
    }
    
    public void setConexaoOutrosProcessos(List<ConexaoOutrosProcessos> conexaoOutrosProcessos) {
        this.conexaoOutrosProcessos = conexaoOutrosProcessos;
    }
    
    public List<Mensagem> getMensagensRecebidas() {
        return mensagensRecebidas;
    }
    
    public void setMensagensRecebidas(List<Mensagem> mensagensRecebidas) {
        this.mensagensRecebidas = mensagensRecebidas;
    }
    
    public List<Mensagem> getMensagensEnviadas() {
        return mensagensEnviadas;
    }
    
    public void setMensagensEnviadas(List<Mensagem> mensagensEnviadas) {
        this.mensagensEnviadas = mensagensEnviadas;
    }
    
    public boolean isSupercoordenador() {
        return supercoordenador;
    }
    
    public void setSupercoordenador(boolean supercoordenador) {
        this.supercoordenador = supercoordenador;
    }
}