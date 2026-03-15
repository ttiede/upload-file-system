package com.uploadplatform.sharing.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public class ShareLink {

    private String token;
    private String fileId;
    private String donoId;
    private Instant expiracao;

    // HashSet para allowlist — contains() O(1), ordem não importa
    private Set<String> emailsPermitidos;

    private Permissao permissao;
    private boolean ativo;

    public ShareLink() {
        this.emailsPermitidos = new HashSet<>();
        this.ativo = true;
    }

    public ShareLink(String token, String fileId, String donoId,
                     Instant expiracao, Set<String> emailsPermitidos, Permissao permissao) {
        this.token = token;
        this.fileId = fileId;
        this.donoId = donoId;
        this.expiracao = expiracao;
        this.emailsPermitidos = new HashSet<>(emailsPermitidos);
        this.permissao = permissao;
        this.ativo = true;
    }

    public boolean expirou() {
        return Instant.now().isAfter(expiracao);
    }

    public boolean ePermitido(String email) {
        if (emailsPermitidos.isEmpty()) return true;
        if (email == null || email.isBlank()) return false;
        return emailsPermitidos.contains(email.toLowerCase().trim());
    }

    public boolean eValido() {
        return ativo && !expirou();
    }

    public String    getToken()                     { return token; }
    public void      setToken(String token)         { this.token = token; }

    public String    getFileId()                    { return fileId; }
    public void      setFileId(String fileId)       { this.fileId = fileId; }

    public String    getDonoId()                    { return donoId; }
    public void      setDonoId(String donoId)       { this.donoId = donoId; }

    public Instant   getExpiracao()                 { return expiracao; }
    public void      setExpiracao(Instant v)        { this.expiracao = v; }

    public Set<String> getEmailsPermitidos()        { return java.util.Collections.unmodifiableSet(emailsPermitidos); }
    public void        setEmailsPermitidos(Set<String> emails) { this.emailsPermitidos = new HashSet<>(emails); }

    public Permissao getPermissao()                 { return permissao; }
    public void      setPermissao(Permissao v)      { this.permissao = v; }

    public boolean   isAtivo()                      { return ativo; }
    public void      setAtivo(boolean ativo)        { this.ativo = ativo; }

    @Override
    public String toString() {
        return "ShareLink{token='%s', fileId='%s', expiracao=%s, ativo=%b}"
                .formatted(token, fileId, expiracao, ativo);
    }

    public enum Permissao {
        VISUALIZAR,
        BAIXAR
    }
}
