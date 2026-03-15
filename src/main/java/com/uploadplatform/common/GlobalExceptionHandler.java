package com.uploadplatform.common;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RespostaErro> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(RespostaErro.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .erro("Bad Request")
                .mensagem(ex.getMessage())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<RespostaErro> handleIllegalState(IllegalStateException ex) {
        log.warn("Unprocessable: {}", ex.getMessage());
        return ResponseEntity.unprocessableEntity().body(RespostaErro.builder()
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .erro("Unprocessable Entity")
                .mensagem(ex.getMessage())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespostaValidacao> handleValidation(MethodArgumentNotValidException ex) {
        // LinkedHashMap preserva a ordem de declaração dos campos no DTO
        Map<String, String> errosCampos = new LinkedHashMap<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            errosCampos.putIfAbsent(erro.getField(), erro.getDefaultMessage());
        }

        return ResponseEntity.badRequest().body(RespostaValidacao.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .erro("Validação falhou")
                .mensagem("Campos inválidos na requisição")
                .errosCampos(errosCampos)
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespostaErro> handleGeneric(Exception ex) {
        log.error("Erro não tratado", ex);
        return ResponseEntity.internalServerError().body(RespostaErro.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .erro("Erro interno")
                .mensagem("Ocorreu um erro inesperado")
                .timestamp(Instant.now())
                .build());
    }

    @Data @Builder
    public static class RespostaErro {
        private int status;
        private String erro;
        private String mensagem;
        private Instant timestamp;
    }

    @Data @Builder
    public static class RespostaValidacao {
        private int status;
        private String erro;
        private String mensagem;
        private Map<String, String> errosCampos;
        private Instant timestamp;
    }
}
