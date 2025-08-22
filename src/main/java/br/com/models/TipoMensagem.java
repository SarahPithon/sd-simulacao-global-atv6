package br.com.models;

public enum TipoMensagem {
    INTERACAO,
    ELEICAO,
    ELEICAO_OK,
    NOVO_LIDER,
    PROCESSO_FALHOU,
    LIDER_ELEITO,
    SOLICITAR_SNAPSHOT,
    RESPOSTA_SNAPSHOT,
    PING,
    PONG,
    MULTICAST
}