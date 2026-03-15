package com.uploadplatform.upload.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class UploadSession {

    private String uploadId;
    private String userId;
    private String nomeArquivo;
    private String contentType;
    private long tamanhoTotal;
    private Instant criadoEm;
    private StatusUpload status;

    // TreeMap ordena partes automaticamente — chegam fora de ordem no upload paralelo
    private TreeMap<Integer, PartInfo> partes;

    public UploadSession() {
        this.partes = new TreeMap<>();
    }

    public UploadSession(String uploadId, String userId, String nomeArquivo,
                         String contentType, long tamanhoTotal,
                         Instant criadoEm, StatusUpload status) {
        this.uploadId = uploadId;
        this.userId = userId;
        this.nomeArquivo = nomeArquivo;
        this.contentType = contentType;
        this.tamanhoTotal = tamanhoTotal;
        this.criadoEm = criadoEm;
        this.status = status;
        this.partes = new TreeMap<>();
    }

    public boolean estaCompleto(int totalEsperado) {
        if (partes.size() != totalEsperado) return false;
        return partes.firstKey() == 1 && partes.lastKey() == totalEsperado;
    }

    public List<PartInfo> getPartesOrdenadas() {
        return new ArrayList<>(partes.values());
    }

    public String getUploadId()               { return uploadId; }
    public void   setUploadId(String v)       { this.uploadId = v; }

    public String getUserId()                 { return userId; }
    public void   setUserId(String v)         { this.userId = v; }

    public String getNomeArquivo()            { return nomeArquivo; }
    public void   setNomeArquivo(String v)    { this.nomeArquivo = v; }

    public String getContentType()            { return contentType; }
    public void   setContentType(String v)    { this.contentType = v; }

    public long   getTamanhoTotal()           { return tamanhoTotal; }
    public void   setTamanhoTotal(long v)     { this.tamanhoTotal = v; }

    public Instant getCriadoEm()              { return criadoEm; }
    public void    setCriadoEm(Instant v)     { this.criadoEm = v; }

    public StatusUpload getStatus()           { return status; }
    public void         setStatus(StatusUpload v) { this.status = v; }

    public TreeMap<Integer, PartInfo> getPartes() { return partes; }

    public enum StatusUpload {
        INICIADO,
        EM_PROGRESSO,
        CONCLUIDO,
        ABORTADO
    }
}
