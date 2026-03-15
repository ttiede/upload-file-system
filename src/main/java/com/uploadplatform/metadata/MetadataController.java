package com.uploadplatform.metadata;

import com.uploadplatform.metadata.model.FileMetadata;
import com.uploadplatform.metadata.model.FileMetadata.FileStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Arquivos")
public class MetadataController {

    private final MetadataService metadataService;

    @GetMapping("/{fileId}")
    @Operation(summary = "Busca metadados de um arquivo")
    public ResponseEntity<FileMetadata> buscar(@PathVariable String fileId) {
        return metadataService.buscarMetadata(fileId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Lista arquivos do usuário com paginação por cursor")
    public ResponseEntity<List<FileMetadata>> listar(
            @PathVariable String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cursor,
            @RequestParam(defaultValue = "20") int pageSize) {

        Instant cursorEfetivo = cursor != null ? cursor : Instant.now();
        int tamanhoEfetivo = Math.min(Math.max(pageSize, 1), 100);

        return ResponseEntity.ok(metadataService.listarArquivos(userId, cursorEfetivo, tamanhoEfetivo));
    }

    @GetMapping("/users/{userId}/agrupados")
    @Operation(summary = "Lista arquivos agrupados por status")
    public ResponseEntity<Map<FileStatus, List<FileMetadata>>> agrupadosPorStatus(@PathVariable String userId) {
        return ResponseEntity.ok(metadataService.agruparPorStatus(userId));
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "Soft delete — marca como DELETADO")
    public ResponseEntity<Void> deletar(@PathVariable String fileId, @RequestParam String userId) {
        metadataService.deletar(fileId, userId);
        return ResponseEntity.noContent().build();
    }
}
