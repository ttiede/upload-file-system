package com.uploadplatform.upload;

import com.uploadplatform.upload.dto.CompleteUploadResponse;
import com.uploadplatform.upload.dto.InitiateUploadResponse;
import com.uploadplatform.upload.dto.PartUploadResponse;
import com.uploadplatform.upload.model.PartInfo;
import com.uploadplatform.upload.model.UploadSession;
import com.uploadplatform.upload.model.UploadSession.StatusUpload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class UploadService {

    private final ConcurrentHashMap<String, UploadSession> uploadsAtivos =
            new ConcurrentHashMap<>(64);

    @Value("${upload.max-file-size:5368709120}")
    private long tamanhoMaximo;

    @Value("${upload.max-parts:10000}")
    private int maxPartes;

    public InitiateUploadResponse iniciarUpload(String userId, String nomeArquivo,
                                                String contentType, long tamanhoTotal) {
        if (tamanhoTotal > tamanhoMaximo) {
            throw new IllegalArgumentException(
                    "Tamanho %d excede o máximo permitido de %d bytes".formatted(tamanhoTotal, tamanhoMaximo));
        }

        String uploadId = UUID.randomUUID().toString();
        Instant agora = Instant.now();

        UploadSession sessao = new UploadSession(uploadId, userId, nomeArquivo,
                contentType, tamanhoTotal, agora, StatusUpload.INICIADO);

        UploadSession existente = uploadsAtivos.putIfAbsent(uploadId, sessao);
        if (existente != null) {
            return iniciarUpload(userId, nomeArquivo, contentType, tamanhoTotal);
        }

        log.info("Upload iniciado: uploadId={} userId={} arquivo={} tamanho={}",
                uploadId, userId, nomeArquivo, tamanhoTotal);

        return InitiateUploadResponse.builder()
                .uploadId(uploadId)
                .fileName(nomeArquivo)
                .userId(userId)
                .createdAt(agora)
                .status(StatusUpload.INICIADO.name())
                .build();
    }

    public PartUploadResponse enviarParte(String uploadId, int numeroParte, byte[] dados) {
        UploadSession sessao = buscarSessao(uploadId);

        if (numeroParte < 1 || numeroParte > maxPartes) {
            throw new IllegalArgumentException(
                    "Número de parte inválido: %d".formatted(numeroParte));
        }
        if (dados == null || dados.length == 0) {
            throw new IllegalArgumentException("Dados da parte não podem ser vazios");
        }

        String eTag = calcularMd5(dados);
        Instant agora = Instant.now();

        PartInfo parte = PartInfo.builder()
                .partNumber(numeroParte)
                .data(dados)
                .eTag(eTag)
                .size(dados.length)
                .uploadedAt(agora)
                .build();

        int totalPartes;
        synchronized (sessao) {
            sessao.getPartes().put(numeroParte, parte);
            sessao.setStatus(StatusUpload.EM_PROGRESSO);
            totalPartes = sessao.getPartes().size();
        }

        return PartUploadResponse.builder()
                .uploadId(uploadId)
                .partNumber(numeroParte)
                .eTag(eTag)
                .size(dados.length)
                .uploadedAt(agora)
                .partsReceivedSoFar(totalPartes)
                .build();
    }

    public CompleteUploadResponse concluirUpload(String uploadId, int totalPartes) {
        UploadSession sessao = buscarSessao(uploadId);

        synchronized (sessao) {
            if (!sessao.estaCompleto(totalPartes)) {
                int recebidas = sessao.getPartes().size();
                throw new IllegalStateException(
                        "Upload incompleto: esperava %d partes, tem %d — uploadId=%s"
                                .formatted(totalPartes, recebidas, uploadId));
            }
        }

        boolean removido = uploadsAtivos.remove(uploadId, sessao);
        if (!removido) {
            throw new IllegalStateException("Sessão %s já foi concluída ou modificada".formatted(uploadId));
        }

        List<PartInfo> partesOrdenadas = sessao.getPartesOrdenadas();

        long tamanhoFinal = 0;
        StringBuilder eTagBuilder = new StringBuilder();
        for (PartInfo p : partesOrdenadas) {
            tamanhoFinal += p.getSize();
            eTagBuilder.append(p.getETag());
        }

        String eTagFinal = calcularMd5(eTagBuilder.toString().getBytes()) + "-" + totalPartes;
        String fileId = UUID.randomUUID().toString();
        String caminhoStorage = "uploads/%s/%s/%s".formatted(sessao.getUserId(), fileId, sessao.getNomeArquivo());

        sessao.setStatus(StatusUpload.CONCLUIDO);

        log.info("Upload concluído: uploadId={} fileId={} partes={} tamanho={}",
                uploadId, fileId, totalPartes, tamanhoFinal);

        return CompleteUploadResponse.builder()
                .fileId(fileId)
                .uploadId(uploadId)
                .fileName(sessao.getNomeArquivo())
                .contentType(sessao.getContentType())
                .fileSize(tamanhoFinal)
                .eTag(eTagFinal)
                .storagePath(caminhoStorage)
                .completedAt(Instant.now())
                .partsAssembled(totalPartes)
                .build();
    }

    public void abortarUpload(String uploadId) {
        UploadSession sessao = uploadsAtivos.remove(uploadId);
        if (sessao == null) {
            throw new IllegalArgumentException("Sessão não encontrada: uploadId=" + uploadId);
        }
        sessao.setStatus(StatusUpload.ABORTADO);
        log.info("Upload abortado: uploadId={} userId={}", uploadId, sessao.getUserId());
    }

    public int getTotalUploadsAtivos() {
        return uploadsAtivos.size();
    }

    private UploadSession buscarSessao(String uploadId) {
        UploadSession sessao = uploadsAtivos.get(uploadId);
        if (sessao == null) {
            throw new IllegalArgumentException("Sessão não encontrada: uploadId=" + uploadId);
        }
        return sessao;
    }

    private String calcularMd5(byte[] dados) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(dados));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 não disponível", e);
        }
    }
}
