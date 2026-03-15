package com.uploadplatform.event;

import com.uploadplatform.metadata.MetadataService;
import com.uploadplatform.metadata.model.FileMetadata;
import com.uploadplatform.metadata.model.FileMetadata.FileStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventProcessorTest {

    @Mock
    private MetadataService metadataService;

    private EventProcessor eventProcessor;

    @BeforeEach
    void setUp() {
        eventProcessor = new EventProcessor(metadataService);
    }

    @Test
    @DisplayName("mesmo eventId processado duas vezes executa o handler uma única vez")
    void idempotencia_mesmEventIdProcessadoUmaVez() {
        String eventId = UUID.randomUUID().toString();
        String fileId  = UUID.randomUUID().toString();

        FileMetadata metadata = criarMetadata(fileId, FileStatus.PROCESSANDO);
        when(metadataService.buscarMetadata(fileId)).thenReturn(Optional.of(metadata));
        when(metadataService.salvarMetadata(any())).thenReturn(metadata);

        eventProcessor.processar(eventId, fileId, EventProcessor.TipoEvento.ANTIVIRUS);
        eventProcessor.processar(eventId, fileId, EventProcessor.TipoEvento.ANTIVIRUS);

        verify(metadataService, times(1)).salvarMetadata(any(FileMetadata.class));
        assertThat(eventProcessor.getTotalProcessados()).isEqualTo(1);
    }

    @Test
    @DisplayName("eventIds diferentes são processados independentemente")
    void eventIdsDiferentes_processadosIndependentemente() {
        String fileId = UUID.randomUUID().toString();
        FileMetadata metadata = criarMetadata(fileId, FileStatus.PROCESSANDO);
        when(metadataService.buscarMetadata(fileId)).thenReturn(Optional.of(metadata));
        when(metadataService.salvarMetadata(any())).thenReturn(metadata);

        eventProcessor.processar("evento-1", fileId, EventProcessor.TipoEvento.ANTIVIRUS);
        eventProcessor.processar("evento-2", fileId, EventProcessor.TipoEvento.ANTIVIRUS);

        verify(metadataService, times(2)).salvarMetadata(any(FileMetadata.class));
        assertThat(eventProcessor.getTotalProcessados()).isEqualTo(2);
    }

    @Test
    @DisplayName("20 threads com o mesmo eventId — salvarMetadata chamado exatamente uma vez")
    void concorrencia_idempotenciaComMultiplasThreads() throws InterruptedException {
        String eventId = UUID.randomUUID().toString();
        String fileId  = UUID.randomUUID().toString();

        FileMetadata metadata = criarMetadata(fileId, FileStatus.PROCESSANDO);
        when(metadataService.buscarMetadata(fileId)).thenReturn(Optional.of(metadata));
        when(metadataService.salvarMetadata(any())).thenReturn(metadata);

        int totalThreads = 20;
        CountDownLatch inicio  = new CountDownLatch(1);
        CountDownLatch fim     = new CountDownLatch(totalThreads);

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    inicio.await();
                    eventProcessor.processar(eventId, fileId, EventProcessor.TipoEvento.ANTIVIRUS);
                } catch (Exception ignored) {
                } finally {
                    fim.countDown();
                }
            });
        }

        inicio.countDown();
        fim.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        verify(metadataService, times(1)).salvarMetadata(any(FileMetadata.class));
    }

    @Test
    @DisplayName("evento THUMBNAIL processado sem erros para arquivo de imagem")
    void thumbnail_processadoComSucesso() {
        String fileId = UUID.randomUUID().toString();
        FileMetadata metadata = criarMetadata(fileId, FileStatus.PROCESSANDO);
        metadata.setContentType("image/jpeg");
        when(metadataService.buscarMetadata(fileId)).thenReturn(Optional.of(metadata));

        eventProcessor.processar(UUID.randomUUID().toString(), fileId, EventProcessor.TipoEvento.THUMBNAIL);

        assertThat(eventProcessor.getTotalProcessados()).isEqualTo(1);
    }

    @Test
    @DisplayName("evento INDEXAR_METADATA processado sem erros")
    void indexacao_processadaComSucesso() {
        String fileId = UUID.randomUUID().toString();
        FileMetadata metadata = criarMetadata(fileId, FileStatus.PROCESSANDO);
        when(metadataService.buscarMetadata(fileId)).thenReturn(Optional.of(metadata));

        eventProcessor.processar(UUID.randomUUID().toString(), fileId, EventProcessor.TipoEvento.INDEXAR_METADATA);

        assertThat(eventProcessor.getTotalProcessados()).isEqualTo(1);
    }

    private FileMetadata criarMetadata(String fileId, FileStatus status) {
        return new FileMetadata(fileId, "user-1", "test.pdf", "application/pdf",
                1024L, "uploads/user-1/" + fileId, "abc123", status);
    }
}
