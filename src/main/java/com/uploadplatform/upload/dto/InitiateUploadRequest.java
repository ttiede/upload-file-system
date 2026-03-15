package com.uploadplatform.upload.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/v1/uploads/initiate.
 *
 * All fields are validated by Spring's Bean Validation before the service layer
 * is ever called, so the service can assume these invariants hold.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateUploadRequest {

    /**
     * The ID of the user initiating the upload.
     * In a production system this would typically be extracted from a JWT claim
     * rather than supplied in the request body, but it is kept here to keep the
     * example self-contained.
     */
    @NotBlank(message = "userId must not be blank")
    private String userId;

    /**
     * Client-supplied original file name, e.g. "backup-2024-01.tar.gz".
     * Length bounded to 255 characters to prevent oversized strings reaching
     * the database.
     */
    @NotBlank(message = "fileName must not be blank")
    @Size(max = 255, message = "fileName must be 255 characters or fewer")
    private String fileName;

    /**
     * MIME type of the file, e.g. "application/pdf", "video/mp4".
     * The client declares this; the server may override it after scanning.
     */
    @NotBlank(message = "contentType must not be blank")
    private String contentType;

    /**
     * Declared total size of the file in bytes.
     * The server uses this to pre-validate that the upload will not exceed
     * the configured maximum (application.yml: upload.max-file-size).
     * Minimum 1 byte — zero-length files are rejected.
     */
    @NotNull(message = "totalSize must not be null")
    @Min(value = 1, message = "totalSize must be at least 1 byte")
    private Long totalSize;
}
