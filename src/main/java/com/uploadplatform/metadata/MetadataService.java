package com.uploadplatform.metadata;

import com.uploadplatform.cache.LRUCache;
import com.uploadplatform.metadata.model.FileMetadata;
import com.uploadplatform.metadata.model.FileMetadata.FileStatus;
import com.uploadplatform.metadata.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataService {

    private final FileMetadataRepository repository;

    // L1 — LRU local, eviction automático pelo LinkedHashMap com accessOrder
    private final LRUCache<String, FileMetadata> cacheLocal = new LRUCache<>(10_000);

    // L2 — simula Redis em memória
    private final ConcurrentHashMap<String, FileMetadata> cacheRedis = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public Optional<FileMetadata> buscarMetadata(String fileId) {
        FileMetadata emCache = cacheLocal.get(fileId);
        if (emCache != null) {
            log.debug("L1 hit: fileId={}", fileId);
            return Optional.of(emCache);
        }

        FileMetadata noRedis = cacheRedis.get(fileId);
        if (noRedis != null) {
            log.debug("L2 hit: fileId={}", fileId);
            cacheLocal.put(fileId, noRedis);
            return Optional.of(noRedis);
        }

        Optional<FileMetadata> doBanco = repository.findById(fileId);
        doBanco.ifPresent(m -> {
            cacheRedis.put(fileId, m);
            cacheLocal.put(fileId, m);
        });

        return doBanco;
    }

    @Transactional
    public FileMetadata salvarMetadata(FileMetadata metadata) {
        if (metadata.getCriadoEm() == null) metadata.setCriadoEm(Instant.now());
        metadata.setAtualizadoEm(Instant.now());

        FileMetadata salvo = repository.save(metadata);
        invalidarCache(salvo.getFileId());

        log.info("Metadata salvo: fileId={} status={}", salvo.getFileId(), salvo.getStatus());
        return salvo;
    }

    @Transactional(readOnly = true)
    public List<FileMetadata> listarArquivos(String userId, Instant cursor, int tamanhoPagina) {
        List<FileMetadata> doBanco = repository.findByUserIdBeforeCursor(
                userId, cursor, PageRequest.of(0, tamanhoPagina));

        if (doBanco.isEmpty()) return new ArrayList<>();

        TreeMap<Instant, FileMetadata> ordenadosPorData = new TreeMap<>();
        for (FileMetadata arquivo : doBanco) {
            ordenadosPorData.put(arquivo.getCriadoEm(), arquivo);
        }

        Collection<FileMetadata> pagina = ordenadosPorData.headMap(cursor, false).values();
        List<FileMetadata> resultado = new ArrayList<>(pagina);

        if (resultado.size() > tamanhoPagina) {
            resultado = resultado.subList(0, tamanhoPagina);
        }

        return resultado;
    }

    @Transactional(readOnly = true)
    public Map<FileStatus, List<FileMetadata>> agruparPorStatus(String userId) {
        Sort porData = Sort.by(Sort.Direction.DESC, "criadoEm");
        List<FileMetadata> arquivos = repository.findByUserIdAndStatusNot(
                userId, FileStatus.DELETADO, porData);

        return arquivos.stream().collect(Collectors.groupingBy(
                FileMetadata::getStatus,
                () -> new EnumMap<>(FileStatus.class),
                Collectors.toList()
        ));
    }

    @Transactional
    public void deletar(String fileId, String userId) {
        FileMetadata metadata = repository.findByFileIdAndUserId(fileId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Arquivo não encontrado ou acesso negado: fileId=%s".formatted(fileId)));

        metadata.setStatus(FileStatus.DELETADO);
        metadata.setAtualizadoEm(Instant.now());
        repository.save(metadata);
        invalidarCache(fileId);

        log.info("Arquivo deletado: fileId={} userId={}", fileId, userId);
    }

    public void invalidarCache(String fileId) {
        cacheLocal.remove(fileId);
        cacheRedis.remove(fileId);
    }

    public int getTamanhoCacheLocal() { return cacheLocal.size(); }
    public int getTamanhoCacheRedis() { return cacheRedis.size(); }
}
