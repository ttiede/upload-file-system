package com.uploadplatform.upload.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Represents one part (chunk) in a multipart upload operation.
 *
 * Parts are numbered starting at 1, matching the S3 multipart upload convention.
 * They are stored inside the parent UploadSession's TreeMap keyed by partNumber,
 * which guarantees they are always iterated in ascending numerical order regardless
 * of the order in which they were received over the network.
 *
 * The {@code eTag} field is a checksum (MD5 hex digest) of the part's raw bytes.
 * It serves two purposes:
 *   1. Data integrity verification on the receiving side.
 *   2. Building the final composite eTag when completing the upload.
 */
@Data
@Builder
public class PartInfo {

    /**
     * 1-based part number. Used as the key in the parent session's
     * TreeMap&lt;Integer, PartInfo&gt; so that iteration order equals assembly order.
     */
    private int partNumber;

    /**
     * Raw bytes of this part. In a production system this would typically be a
     * reference (e.g., a storage path or a streaming handle) rather than an
     * in-memory byte array, to avoid heap pressure for large uploads.
     * Kept as byte[] here so the logic is self-contained and easy to follow.
     */
    private byte[] data;

    /**
     * MD5 hex digest of {@code data}. Computed by UploadService upon receipt
     * and stored here so callers can verify part integrity without re-reading
     * the bytes.
     */
    private String eTag;

    /**
     * Number of bytes in this part. Stored separately so callers can compute
     * the total assembled file size without iterating all byte arrays.
     */
    private long size;

    /**
     * Wall-clock time at which this part was received and stored. Useful for
     * detecting stale in-progress uploads during cleanup/maintenance jobs.
     */
    private Instant uploadedAt;
}
