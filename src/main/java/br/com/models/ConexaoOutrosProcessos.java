package br.com.models;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ConexaoOutrosProcessos {
    private String nomeProcesso;
    private int parametroEleicao;
    private int porta;
    private ObjectOutputStream objectOutputStream;
    private ObjectInputStream objectInputStream;
    
    public ConexaoOutrosProcessos() {}
    
    public String getNomeProcesso() {
        return nomeProcesso;
    }
    
    public void setNomeProcesso(String nomeProcesso) {
        this.nomeProcesso = nomeProcesso;
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
    
    public ObjectOutputStream getObjectOutputStream() {
        return objectOutputStream;
    }
    
    public void setObjectOutputStream(ObjectOutputStream objectOutputStream) {
        this.objectOutputStream = objectOutputStream;
    }
    
    public ObjectInputStream getObjectInputStream() {
        return objectInputStream;
    }
    
    public void setObjectInputStream(ObjectInputStream objectInputStream) {
        this.objectInputStream = objectInputStream;
    }
}