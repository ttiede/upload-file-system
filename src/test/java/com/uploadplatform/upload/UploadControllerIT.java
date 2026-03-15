package com.uploadplatform.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uploadplatform.upload.dto.CompleteUploadRequest;
import com.uploadplatform.upload.dto.InitiateUploadRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("fluxo completo: initiate → part → complete")
    void fluxoCompletoDeUpload() throws Exception {
        InitiateUploadRequest initReq = InitiateUploadRequest.builder()
                .userId("user-it-1")
                .fileName("test.txt")
                .contentType("text/plain")
                .totalSize(11L)
                .build();

        MvcResult initResult = mockMvc.perform(post("/api/v1/uploads/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadId").isNotEmpty())
                .andReturn();

        String body = initResult.getResponse().getContentAsString();
        String uploadId = objectMapper.readTree(body).get("uploadId").asText();
        assertThat(uploadId).isNotBlank();

        byte[] dados = "hello world".getBytes();
        mockMvc.perform(put("/api/v1/uploads/{id}/parts/{n}", uploadId, 1)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(dados))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partNumber").value(1))
                .andExpect(jsonPath("$.eTag").isNotEmpty());

        CompleteUploadRequest completeReq = CompleteUploadRequest.builder()
                .totalParts(1)
                .build();

        mockMvc.perform(post("/api/v1/uploads/{id}/complete", uploadId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").isNotEmpty())
                .andExpect(jsonPath("$.partsAssembled").value(1));
    }

    @Test
    @DisplayName("initiate com body inválido retorna 400 com detalhes por campo")
    void initiate_bodyInvalido_retorna400() throws Exception {
        mockMvc.perform(post("/api/v1/uploads/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errosCampos").isMap());
    }

    @Test
    @DisplayName("enviar parte para uploadId inexistente retorna 400")
    void enviarParte_uploadIdInexistente_retorna400() throws Exception {
        mockMvc.perform(put("/api/v1/uploads/{id}/parts/{n}", "nao-existe", 1)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("dados".getBytes()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("abort retorna 204")
    void abort_retorna204() throws Exception {
        InitiateUploadRequest req = InitiateUploadRequest.builder()
                .userId("user-it-2").fileName("del.txt")
                .contentType("text/plain").totalSize(5L).build();

        MvcResult result = mockMvc.perform(post("/api/v1/uploads/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String uploadId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("uploadId").asText();

        mockMvc.perform(delete("/api/v1/uploads/{id}", uploadId))
                .andExpect(status().isNoContent());
    }
}
