package com.uploadplatform.event;

import com.uploadplatform.metadata.MetadataService;
import com.uploadplatform.metadata.model.FileMetadata;
import com.uploadplatform.metadata.model.FileMetadata.FileStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventProcessor {

    private final MetadataService metadataService;

    // add() retorna false se já processado — idempotência sem lock global
    private final Set<String> eventosProcessados = ConcurrentHashMap.newKeySet();

    private final Map<String, Integer> tentativas = new ConcurrentHashMap<>();

    private static final int MAX_TENTATIVAS = 3;

    public void processar(String eventId, String fileId, TipoEvento tipo) {
        if (!eventosProcessados.add(eventId)) {
            log.debug("Evento duplicado ignorado: eventId={}", eventId);
            return;
        }

        log.info("Processando evento: eventId={} fileId={} tipo={}", eventId, fileId, tipo);

        try {
            switch (tipo) {
                case ANTIVIRUS       -> processarAntivirus(fileId);
                case THUMBNAIL       -> processarThumbnail(fileId);
                case INDEXAR_METADATA -> indexarMetadata(fileId);
            }
            tentativas.remove(eventId);
        } catch (Exception e) {
            log.error("Falha ao processar evento: eventId={} erro={}", eventId, e.getMessage());
            tentativas.merge(eventId, 1, Integer::sum);
            eventosProcessados.remove(eventId);
            throw new RuntimeException("Falha no processamento do evento: " + eventId, e);
        }
    }

    public void processarAntivirus(String fileId) {
        FileMetadata metadata = metadataService.buscarMetadata(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Arquivo não encontrado: " + fileId));

        boolean limpo = simularScanAntivirus(metadata.getStoragePath());
        metadata.setStatus(limpo ? FileStatus.DISPONIVEL : FileStatus.DELETADO);
        metadataService.salvarMetadata(metadata);

        log.info("Antivírus: {} fileId={}", limpo ? "LIMPO" : "INFECTADO", fileId);
    }

    public void processarThumbnail(String fileId) {
        FileMetadata metadata = metadataService.buscarMetadata(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Arquivo não encontrado: " + fileId));

        String tipo = metadata.getContentType();
        if (tipo != null && (tipo.startsWith("image/") || tipo.startsWith("video/"))) {
            String caminho = "thumbnails/%s/thumb_%s".formatted(metadata.getUserId(), fileId);
            log.info("Thumbnail gerado: {} fileId={}", caminho, fileId);
        }
    }

    public void indexarMetadata(String fileId) {
        FileMetadata metadata = metadataService.buscarMetadata(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Arquivo não encontrado: " + fileId));

        log.info("Indexado: fileId={} nome={} userId={}", fileId, metadata.getNomeArquivo(), metadata.getUserId());
    }

    public void reprocessarFalhas() {
        tentativas.entrySet().stream()
                .filter(e -> e.getValue() > 0 && e.getValue() <= MAX_TENTATIVAS)
                .forEach(e -> log.info("Reprocessando: eventId={} tentativa={}/{}",
                        e.getKey(), e.getValue() + 1, MAX_TENTATIVAS));

        tentativas.entrySet().removeIf(e -> {
            if (e.getValue() > MAX_TENTATIVAS) {
                log.error("Evento abandonado após {} tentativas: eventId={}", MAX_TENTATIVAS, e.getKey());
                return true;
            }
            return false;
        });
    }

    public int getTotalProcessados() { return eventosProcessados.size(); }
    public int getTotalPendentes()   { return tentativas.size(); }

    private boolean simularScanAntivirus(String caminho) {
        return true;
    }

    public enum TipoEvento {
        ANTIVIRUS,
        THUMBNAIL,
        INDEXAR_METADATA
    }
}
