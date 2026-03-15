package com.uploadplatform.sharing;

import com.uploadplatform.sharing.model.ShareLink;
import com.uploadplatform.sharing.model.ShareLink.Permissao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SharingService {

    private final ConcurrentHashMap<String, ShareLink> linksAtivos = new ConcurrentHashMap<>();

    public ShareLink criarLink(String fileId, String donoId, Duration ttl,
                               Set<String> emailsPermitidos, Permissao permissao) {
        String token = UUID.randomUUID().toString();
        Instant expiracao = Instant.now().plus(ttl);

        ShareLink link = new ShareLink(token, fileId, donoId, expiracao, emailsPermitidos, permissao);

        ShareLink existente = linksAtivos.putIfAbsent(token, link);
        if (existente != null) {
            return criarLink(fileId, donoId, ttl, emailsPermitidos, permissao);
        }

        log.info("Link criado: token={} fileId={} expira={}", token, fileId, expiracao);
        return link;
    }

    public Optional<ShareLink> validarLink(String token, String emailSolicitante) {
        ShareLink link = linksAtivos.get(token);

        if (link == null || !link.isAtivo() || link.expirou()) return Optional.empty();

        if (!link.ePermitido(emailSolicitante)) return Optional.empty();

        return Optional.of(link);
    }

    public void revogarLink(String token, String donoId) {
        ShareLink link = linksAtivos.get(token);

        if (link == null) throw new IllegalArgumentException("Link não encontrado: " + token);
        if (!link.getDonoId().equals(donoId))
            throw new IllegalArgumentException("Acesso negado: token=%s".formatted(token));

        link.setAtivo(false);
        linksAtivos.remove(token);

        log.info("Link revogado: token={} fileId={}", token, link.getFileId());
    }

    public List<ShareLink> buscarLinksAtivos(String fileId) {
        return linksAtivos.values().stream()
                .filter(l -> fileId.equals(l.getFileId()))
                .filter(ShareLink::isAtivo)
                .filter(l -> !l.expirou())
                .collect(Collectors.toList());
    }

    public Map<String, Long> estatisticas() {
        return linksAtivos.values().stream()
                .filter(ShareLink::isAtivo)
                .filter(l -> !l.expirou())
                .collect(Collectors.groupingBy(ShareLink::getFileId, Collectors.counting()));
    }

    public void limparExpirados() {
        for (Map.Entry<String, ShareLink> entry : linksAtivos.entrySet()) {
            ShareLink link = entry.getValue();
            if (!link.isAtivo() || link.expirou()) {
                linksAtivos.remove(entry.getKey(), link);
            }
        }
    }

    public int getTotalLinks() { return linksAtivos.size(); }
}
