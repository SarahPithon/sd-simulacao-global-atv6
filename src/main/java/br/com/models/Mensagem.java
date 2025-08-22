package br.com.models;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public class Mensagem implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private UUID idMensagem;
    private String processoRemetente;
    private String processoDestinatario;
    private LocalDateTime dataMensagem;
    private int clockMensagem;
    private String conteudoMensagem;
    private TipoMensagem tipoMensagem;
    
    public Mensagem() {}
    
    public UUID getIdMensagem() {
        return idMensagem;
    }
    
    public void setIdMensagem(UUID idMensagem) {
        this.idMensagem = idMensagem;
    }
    
    public String getProcessoRemetente() {
        return processoRemetente;
    }
    
    public void setProcessoRemetente(String processoRemetente) {
        this.processoRemetente = processoRemetente;
    }
    
    public String getProcessoDestinatario() {
        return processoDestinatario;
    }
    
    public void setProcessoDestinatario(String processoDestinatario) {
        this.processoDestinatario = processoDestinatario;
    }
    
    public LocalDateTime getDataMensagem() {
        return dataMensagem;
    }
    
    public void setDataMensagem(LocalDateTime dataMensagem) {
        this.dataMensagem = dataMensagem;
    }
    
    public int getClockMensagem() {
        return clockMensagem;
    }
    
    public void setClockMensagem(int clockMensagem) {
        this.clockMensagem = clockMensagem;
    }
    
    public String getConteudoMensagem() {
        return conteudoMensagem;
    }
    
    public void setConteudoMensagem(String conteudoMensagem) {
        this.conteudoMensagem = conteudoMensagem;
    }
    
    public TipoMensagem getTipoMensagem() {
        return tipoMensagem;
    }
    
    public void setTipoMensagem(TipoMensagem tipoMensagem) {
        this.tipoMensagem = tipoMensagem;
    }
}