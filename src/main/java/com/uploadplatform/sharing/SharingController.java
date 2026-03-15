package com.uploadplatform.sharing;

import com.uploadplatform.sharing.model.ShareLink;
import com.uploadplatform.sharing.model.ShareLink.SharePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * REST controller for file sharing operations.
 *
 * <p>Share links allow file owners to grant time-limited, optionally email-restricted
 * access to specific files. The sharing feature demonstrates:
 * <ul>
 *   <li>ConcurrentHashMap for concurrent link storage (in SharingService)</li>
 *   <li>HashSet for O(1) email allowlist membership checks (in ShareLink)</li>
 *   <li>Map.values() → Collection → stream().collect() conversion pattern</li>
 *   <li>Collectors.groupingBy + Collectors.counting() for aggregation</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/share")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "File Sharing", description = "Create and manage file share links")
public class SharingController {

    private final SharingService sharingService;

    // -----------------------------------------------------------------------
    // POST /api/v1/share
    // -----------------------------------------------------------------------

    /**
     * Creates a new share link for a file.
     *
     * <p>The request body includes an optional list of allowed email addresses.
     * Internally these are stored in a {@link HashSet} for O(1) membership checks
     * during validation. An empty set means the link is public.</p>
     *
     * @param request validated share link creation parameters
     * @return 201 Created with the new ShareLink
     */
    @PostMapping
    @Operation(
            summary = "Create share link",
            description = "Generates a token-based share link with optional email restriction and TTL",
            responses = {
                @ApiResponse(responseCode = "201", description = "Share link created"),
                @ApiResponse(responseCode = "400", description = "Invalid request")
            }
    )
    public ResponseEntity<ShareLink> createShareLink(
            @Valid @RequestBody CreateShareLinkRequest request) {

        // Convert the request's List<String> emails to a Set<String> before passing
        // to the service. The service stores them in a HashSet for O(1) access control.
        // Using a Set here makes the intent explicit: email addresses are a set (no duplicates).
        Set<String> emailSet = request.getAllowedEmails() != null
                ? new HashSet<>(request.getAllowedEmails())
                : new HashSet<>();

        Duration ttl = Duration.ofSeconds(request.getTtlSeconds());

        ShareLink link = sharingService.createShareLink(
                request.getFileId(),
                request.getOwnerId(),
                ttl,
                emailSet,
                request.getPermission());

        log.info("Share link created via API: token={}, fileId={}",
                link.getToken(), link.getFileId());

        return ResponseEntity.status(HttpStatus.CREATED).body(link);
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/share/{token}/validate
    // -----------------------------------------------------------------------

    /**
     * Validates a share link and returns the linked file information if valid.
     *
     * <p>The service performs a ConcurrentHashMap.get() (O(1)) for the token,
     * then calls ShareLink.isAllowed(email) which uses HashSet.contains() (O(1)).
     * The combined validation is effectively O(1) — no iteration required.</p>
     *
     * @param token          the share token from the URL
     * @param requesterEmail the email of the user attempting to use the link
     * @return 200 OK with ValidationResponse if valid, 403 Forbidden if invalid
     */
    @GetMapping("/{token}/validate")
    @Operation(
            summary = "Validate share link",
            description = "Checks expiry, revocation status, and email allowlist (HashSet O(1) lookup)"
    )
    public ResponseEntity<ValidationResponse> validateLink(
            @PathVariable String token,
            @Parameter(description = "Requester's email address")
            @RequestParam(required = false) String requesterEmail) {

        Optional<ShareLink> validLink = sharingService.validateLink(token, requesterEmail);

        if (validLink.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ShareLink link = validLink.get();
        ValidationResponse response = ValidationResponse.builder()
                .valid(true)
                .token(token)
                .fileId(link.getFileId())
                .permission(link.getPermission())
                .expiresAt(link.getExpiresAt())
                .build();

        return ResponseEntity.ok(response);
    }

    // -----------------------------------------------------------------------
    // DELETE /api/v1/share/{token}
    // -----------------------------------------------------------------------

    /**
     * Revokes a share link.
     *
     * <p>Only the owner who created the link can revoke it. The service verifies
     * ownerId before marking the link inactive and removing it from the
     * ConcurrentHashMap.</p>
     *
     * @param token   the token to revoke
     * @param ownerId must match the original creator's ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{token}")
    @Operation(
            summary = "Revoke share link",
            description = "Marks the link as inactive and removes it from the in-memory registry"
    )
    public ResponseEntity<Void> revokeLink(
            @PathVariable String token,
            @Parameter(description = "ID of the user revoking the link (must be owner)")
            @RequestParam String ownerId) {

        sharingService.revokeLink(token, ownerId);
        log.info("Share link revoked via API: token={}", token);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/share/files/{fileId}/links
    // -----------------------------------------------------------------------

    /**
     * Returns all active, non-expired share links for a specific file.
     *
     * <p>The service iterates {@code ConcurrentHashMap.values()} (a Collection)
     * and filters via a stream pipeline into a new List. The controller simply
     * returns the List as-is — JSON serialisation works on any Iterable.</p>
     *
     * @param fileId  the file whose share links to list
     * @param ownerId must match the file owner (access control)
     * @return 200 OK with List of active ShareLinks for this file
     */
    @GetMapping("/files/{fileId}/links")
    @Operation(
            summary = "List active links for a file",
            description = "Returns active share links. Demonstrates Map.values() → Collection → List conversion."
    )
    public ResponseEntity<List<ShareLink>> getActiveLinksForFile(
            @PathVariable String fileId,
            @Parameter(description = "File owner's user ID")
            @RequestParam String ownerId) {

        // The service returns a List<ShareLink> built from ConcurrentHashMap.values()
        // via stream().filter().collect(). See SharingService.getActiveLinksForFile().
        List<ShareLink> links = sharingService.getActiveLinksForFile(fileId);
        return ResponseEntity.ok(links);
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/share/stats
    // -----------------------------------------------------------------------

    /**
     * Returns aggregated statistics about active share links, grouped by file.
     *
     * <p>Demonstrates {@link java.util.stream.Collectors#groupingBy} with
     * {@link java.util.stream.Collectors#counting()} — produces
     * {@code Map<String, Long>} (fileId → link count) from the
     * ConcurrentHashMap values stream.</p>
     *
     * @return 200 OK with a map of fileId → active link count
     */
    @GetMapping("/stats")
    @Operation(
            summary = "Get share link statistics",
            description = "Groups active links by fileId using Collectors.groupingBy + Collectors.counting()"
    )
    public ResponseEntity<Map<String, Long>> getLinkStats() {
        Map<String, Long> stats = sharingService.getLinkStats();
        return ResponseEntity.ok(stats);
    }

    // -----------------------------------------------------------------------
    // Nested request / response DTOs (kept local to the controller since they
    // are only used by this controller's endpoints)
    // -----------------------------------------------------------------------

    /**
     * Request body for POST /api/v1/share.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateShareLinkRequest {

        @NotBlank(message = "fileId must not be blank")
        private String fileId;

        @NotBlank(message = "ownerId must not be blank")
        private String ownerId;

        /** TTL in seconds. Minimum 60 seconds to prevent immediately expired links. */
        @Min(value = 60, message = "ttlSeconds must be at least 60")
        private long ttlSeconds;

        /**
         * Optional list of email addresses to restrict access to.
         * Null or empty means the link is public (any requester allowed).
         * The controller converts this to a HashSet before passing to the service.
         */
        private List<String> allowedEmails;

        @NotNull(message = "permission must not be null")
        private SharePermission permission;
    }

    /**
     * Response body for GET /api/v1/share/{token}/validate.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResponse {
        private boolean valid;
        private String token;
        private String fileId;
        private SharePermission permission;
        private Instant expiresAt;
    }
}
