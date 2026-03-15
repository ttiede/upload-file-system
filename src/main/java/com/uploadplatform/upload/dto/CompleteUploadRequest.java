package com.uploadplatform.upload.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/v1/uploads/{uploadId}/complete.
 *
 * The client declares how many parts it uploaded. The service compares this
 * against the number of parts actually present in the session's TreeMap to
 * detect any missing chunks before finalising the file.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadRequest {

    /**
     * Total number of parts the client uploaded for this session.
     * Must be at least 1 (single-part uploads are allowed).
     */
    @NotNull(message = "totalParts must not be null")
    @Min(value = 1, message = "totalParts must be at least 1")
    private Integer totalParts;
}
