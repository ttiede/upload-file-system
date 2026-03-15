package com.uploadplatform.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response body for POST /api/v1/uploads/{uploadId}/complete (HTTP 200 OK).
 *
 * After successful completion the upload session is removed from the in-memory
 * ConcurrentHashMap and the assembled file metadata is persisted to the database.
 * This response gives the client all the information needed to reference the
 * stored file going forward.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadResponse {

    /** The stable, permanent identifier for the assembled file. */
    private String fileId;

    /** Echo of the original uploadId that was just completed. */
    private String uploadId;

    /** File name as originally declared by the client. */
    private String fileName;

    /** Detected or declared MIME type. */
    private String contentType;

    /** Total assembled file size in bytes. */
    private long fileSize;

    /**
     * Composite eTag computed from the concatenated part eTags.
     * Follows the S3 multipart eTag convention: MD5(md5_part1 + md5_part2 + …)-N
     * where N is the number of parts.
     */
    private String eTag;

    /** Storage path / object key where the assembled file was written. */
    private String storagePath;

    /** Timestamp when the complete operation finished. */
    private Instant completedAt;

    /** Number of parts that were assembled. */
    private int partsAssembled;
}
