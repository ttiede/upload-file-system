package com.uploadplatform.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response body for POST /api/v1/uploads/initiate (HTTP 201 Created).
 *
 * The {@code uploadId} is the token the client must supply in every subsequent
 * part upload and in the final complete/abort call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateUploadResponse {

    /** Unique identifier for this upload session. */
    private String uploadId;

    /** Echo of the fileName from the request, for client-side confirmation. */
    private String fileName;

    /** Echo of the userId who initiated the upload. */
    private String userId;

    /** Server-side timestamp when the session was created. */
    private Instant createdAt;

    /** Human-readable session status — will be "INITIATED" at this point. */
    private String status;
}
