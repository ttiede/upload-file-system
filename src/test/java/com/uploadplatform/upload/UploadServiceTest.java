package com.uploadplatform.upload;

import com.uploadplatform.upload.dto.CompleteUploadResponse;
import com.uploadplatform.upload.dto.InitiateUploadResponse;
import com.uploadplatform.upload.dto.PartUploadResponse;
import com.uploadplatform.upload.model.UploadSession;
import com.uploadplatform.upload.model.UploadSession.StatusUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadServiceTest {

    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new UploadService();
    }

    @Test
    @DisplayName("iniciarUpload retorna uploadId e status INICIADO")
    void iniciarUpload_retornaUploadId() {
        InitiateUploadResponse response = uploadService.iniciarUpload(
                "user-1", "arquivo.pdf", "application/pdf", 1024L);

        assertThat(response.getUploadId()).isNotBlank();
        assertThat(response.getStatus()).isEqualTo(StatusUpload.INICIADO.name());
        assertThat(response.getFileName()).isEqualTo("arquivo.pdf");
        assertThat(response.getUserId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("iniciarUpload registra a sessão no mapa ativo")
    void iniciarUpload_incrementaContador() {
        assertThat(uploadService.getTotalUploadsAtivos()).isZero();

        uploadService.iniciarUpload("u1", "a.txt", "text/plain", 100L);
        uploadService.iniciarUpload("u2", "b.txt", "text/plain", 200L);

        assertThat(uploadService.getTotalUploadsAtivos()).isEqualTo(2);
    }

    @Test
    @DisplayName("partes enviadas fora de ordem são remontadas em ordem pelo TreeMap")
    void enviarParte_treemapOrdenaPorNumero() {
        InitiateUploadResponse init = uploadService.iniciarUpload(
                "user-1", "video.mp4", "video/mp4", 300L);
        String uploadId = init.getUploadId();

        byte[] parte3 = "PARTE3".getBytes();
        byte[] parte1 = "PARTE1".getBytes();
        byte[] parte2 = "PARTE2".getBytes();

        uploadService.enviarParte(uploadId, 3, parte3);
        uploadService.enviarParte(uploadId, 1, parte1);
        uploadService.enviarParte(uploadId, 2, parte2);

        CompleteUploadResponse response = uploadService.concluirUpload(uploadId, 3);

        assertThat(response.getPartsAssembled()).isEqualTo(3);
        assertThat(response.getFileSize()).isEqualTo(parte1.length + parte2.length + parte3.length);
    }

    @Test
    @DisplayName("enviar a mesma parte duas vezes sobrescreve idempotentemente")
    void enviarParte_duplicadaSobrescreve() {
        InitiateUploadResponse init = uploadService.iniciarUpload(
                "user-1", "doc.pdf", "application/pdf", 200L);
        String uploadId = init.getUploadId();

        PartUploadResponse primeiro = uploadService.enviarParte(uploadId, 1, "dados-originais".getBytes());
        PartUploadResponse segundo  = uploadService.enviarParte(uploadId, 1, "dados-atualizados".getBytes());

        assertThat(primeiro.getETag()).isNotEqualTo(segundo.getETag());
        assertThat(segundo.getPartsReceivedSoFar()).isEqualTo(1);
    }

    @Test
    @DisplayName("enviarParte lança exceção para sessão inexistente")
    void enviarParte_sessaoInexistente() {
        assertThatThrownBy(() -> uploadService.enviarParte("inexistente", 1, "dado".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inexistente");
    }

    @Test
    @DisplayName("concluirUpload falha quando faltam partes")
    void concluirUpload_faltandoPartes() {
        InitiateUploadResponse init = uploadService.iniciarUpload(
                "user-1", "arquivo.zip", "application/zip", 300L);
        String uploadId = init.getUploadId();

        uploadService.enviarParte(uploadId, 1, "chunk1".getBytes());

        assertThatThrownBy(() -> uploadService.concluirUpload(uploadId, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("esperava 3 partes");
    }

    @Test
    @DisplayName("concluirUpload remove a sessão do mapa ativo")
    void concluirUpload_removeSessao() {
        InitiateUploadResponse init = uploadService.iniciarUpload(
                "user-1", "foto.jpg", "image/jpeg", 50L);
        String uploadId = init.getUploadId();

        uploadService.enviarParte(uploadId, 1, "pixels".getBytes());
        uploadService.concluirUpload(uploadId, 1);

        assertThat(uploadService.getTotalUploadsAtivos()).isZero();
        assertThatThrownBy(() -> uploadService.enviarParte(uploadId, 2, "mais".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("abortarUpload remove a sessão e decrementa o contador")
    void abortarUpload_removeSessao() {
        InitiateUploadResponse init = uploadService.iniciarUpload(
                "user-1", "grande.iso", "application/octet-stream", 1024L);

        assertThat(uploadService.getTotalUploadsAtivos()).isEqualTo(1);
        uploadService.abortarUpload(init.getUploadId());
        assertThat(uploadService.getTotalUploadsAtivos()).isZero();
    }

    @Test
    @DisplayName("abortarUpload lança exceção para uploadId inexistente")
    void abortarUpload_idInexistente() {
        assertThatThrownBy(() -> uploadService.abortarUpload("id-fantasma"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("UploadSession.estaCompleto retorna false quando há gap entre partes")
    void uploadSession_estaCompletoFalseComGap() {
        UploadSession sessao = new UploadSession();
        sessao.getPartes().put(1, null);
        sessao.getPartes().put(3, null);

        assertThat(sessao.estaCompleto(3)).isFalse();
    }

    @Test
    @DisplayName("UploadSession.getPartesOrdenadas retorna partes em ordem crescente")
    void uploadSession_partesOrdenadas() {
        UploadSession sessao = new UploadSession();

        com.uploadplatform.upload.model.PartInfo p3 =
                com.uploadplatform.upload.model.PartInfo.builder()
                        .partNumber(3).data(new byte[0]).eTag("e3").size(0)
                        .uploadedAt(java.time.Instant.now()).build();
        com.uploadplatform.upload.model.PartInfo p1 =
                com.uploadplatform.upload.model.PartInfo.builder()
                        .partNumber(1).data(new byte[0]).eTag("e1").size(0)
                        .uploadedAt(java.time.Instant.now()).build();
        com.uploadplatform.upload.model.PartInfo p2 =
                com.uploadplatform.upload.model.PartInfo.builder()
                        .partNumber(2).data(new byte[0]).eTag("e2").size(0)
                        .uploadedAt(java.time.Instant.now()).build();

        sessao.getPartes().put(3, p3);
        sessao.getPartes().put(1, p1);
        sessao.getPartes().put(2, p2);

        var ordenadas = sessao.getPartesOrdenadas();

        assertThat(ordenadas).hasSize(3);
        assertThat(ordenadas.get(0).getPartNumber()).isEqualTo(1);
        assertThat(ordenadas.get(1).getPartNumber()).isEqualTo(2);
        assertThat(ordenadas.get(2).getPartNumber()).isEqualTo(3);
    }
}
