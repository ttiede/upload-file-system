package com.uploadplatform.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response body for PUT /api/v1/uploads/{uploadId}/parts/{partNumber} (HTTP 200 OK).
 *
 * Returned after a single part has been received, validated, and stored in the
 * session's TreeMap. The client should store the {@code eTag} value to include
 * in the complete request for server-side integrity verification (mirrors the
 * S3 multipart upload API contract).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartUploadResponse {

    /** The upload session this part belongs to. */
    private String uploadId;

    /** 1-based index of the part that was just stored. */
    private int partNumber;

    /**
     * MD5 hex digest of the part's bytes, computed server-side.
     * The client should retain this for the CompleteUploadRequest.
     */
    private String eTag;

    /** Number of bytes received in this part. */
    private long size;

    /** Timestamp when the part was stored in the session's TreeMap. */
    private Instant uploadedAt;

    /**
     * How many parts have been stored so far in this session.
     * Gives the client a progress indicator without a separate status call.
     */
    private int partsReceivedSoFar;
}
