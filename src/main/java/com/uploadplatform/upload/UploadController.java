package com.uploadplatform.upload;

import com.uploadplatform.upload.dto.CompleteUploadRequest;
import com.uploadplatform.upload.dto.CompleteUploadResponse;
import com.uploadplatform.upload.dto.InitiateUploadRequest;
import com.uploadplatform.upload.dto.InitiateUploadResponse;
import com.uploadplatform.upload.dto.PartUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Uploads")
public class UploadController {

    private final UploadService uploadService;

    @PostMapping("/initiate")
    @Operation(summary = "Inicia sessão de upload multipart")
    public ResponseEntity<InitiateUploadResponse> iniciar(@Valid @RequestBody InitiateUploadRequest request) {
        log.info("Iniciando upload: userId={} arquivo={} tamanho={}",
                request.getUserId(), request.getFileName(), request.getTotalSize());

        InitiateUploadResponse response = uploadService.iniciarUpload(
                request.getUserId(), request.getFileName(),
                request.getContentType(), request.getTotalSize());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping(value = "/{uploadId}/parts/{numeroParte}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Envia uma parte do upload")
    public ResponseEntity<PartUploadResponse> enviarParte(
            @PathVariable String uploadId,
            @PathVariable @Min(1) @Max(10_000) int numeroParte,
            @RequestBody byte[] dados) {

        PartUploadResponse response = uploadService.enviarParte(uploadId, numeroParte, dados);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{uploadId}/complete")
    @Operation(summary = "Finaliza o upload e remonta o arquivo")
    public ResponseEntity<CompleteUploadResponse> concluir(
            @PathVariable String uploadId,
            @Valid @RequestBody CompleteUploadRequest request) {

        log.info("Concluindo upload: uploadId={} partes={}", uploadId, request.getTotalParts());
        return ResponseEntity.ok(uploadService.concluirUpload(uploadId, request.getTotalParts()));
    }

    @DeleteMapping("/{uploadId}")
    @Operation(summary = "Aborta o upload")
    public ResponseEntity<Void> abortar(@PathVariable String uploadId) {
        uploadService.abortarUpload(uploadId);
        return ResponseEntity.noContent().build();
    }
}
