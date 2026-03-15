package com.uploadplatform.metadata.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "file_metadata")
public class FileMetadata {

    @Id
    @Column(name = "file_id", nullable = false, updatable = false, length = 36)
    private String fileId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "tamanho", nullable = false)
    private long tamanho;

    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    @Column(name = "e_tag")
    private String eTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FileStatus status;

    @Version
    @Column(name = "versao", nullable = false)
    private int versao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected FileMetadata() {}

    public FileMetadata(String fileId, String userId, String nomeArquivo,
                        String contentType, long tamanho, String storagePath,
                        String eTag, FileStatus status) {
        this.fileId = fileId;
        this.userId = userId;
        this.nomeArquivo = nomeArquivo;
        this.contentType = contentType;
        this.tamanho = tamanho;
        this.storagePath = storagePath;
        this.eTag = eTag;
        this.status = status;
        this.criadoEm = Instant.now();
        this.atualizadoEm = this.criadoEm;
    }

    // equals/hashCode só pelo fileId — campos mutáveis fora
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(fileId, ((FileMetadata) o).fileId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fileId);
    }

    @Override
    public String toString() {
        return "FileMetadata{fileId='%s', userId='%s', nome='%s', status=%s}"
                .formatted(fileId, userId, nomeArquivo, status);
    }

    public String      getFileId()                         { return fileId; }
    public void        setFileId(String fileId)            { this.fileId = fileId; }

    public String      getUserId()                         { return userId; }
    public void        setUserId(String userId)            { this.userId = userId; }

    public String      getNomeArquivo()                    { return nomeArquivo; }
    public void        setNomeArquivo(String nomeArquivo)  { this.nomeArquivo = nomeArquivo; }

    public String      getContentType()                    { return contentType; }
    public void        setContentType(String contentType)  { this.contentType = contentType; }

    public long        getTamanho()                        { return tamanho; }
    public void        setTamanho(long tamanho)            { this.tamanho = tamanho; }

    public String      getStoragePath()                    { return storagePath; }
    public void        setStoragePath(String storagePath)  { this.storagePath = storagePath; }

    public String      getETag()                           { return eTag; }
    public void        setETag(String eTag)                { this.eTag = eTag; }

    public FileStatus  getStatus()                         { return status; }
    public void        setStatus(FileStatus status)        { this.status = status; }

    public int         getVersao()                         { return versao; }

    public Instant     getCriadoEm()                       { return criadoEm; }
    public void        setCriadoEm(Instant criadoEm)       { this.criadoEm = criadoEm; }

    public Instant     getAtualizadoEm()                   { return atualizadoEm; }
    public void        setAtualizadoEm(Instant v)          { this.atualizadoEm = v; }

    public enum FileStatus {
        PROCESSANDO,
        DISPONIVEL,
        DELETADO
    }
}
