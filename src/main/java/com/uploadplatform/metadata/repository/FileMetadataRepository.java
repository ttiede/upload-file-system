package com.uploadplatform.metadata.repository;

import com.uploadplatform.metadata.model.FileMetadata;
import com.uploadplatform.metadata.model.FileMetadata.FileStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, String> {

    List<FileMetadata> findByUserId(String userId);

    List<FileMetadata> findByUserIdAndStatusNot(String userId, FileStatus status, Sort sort);

    Optional<FileMetadata> findByFileIdAndUserId(String fileId, String userId);

    @Query("""
            SELECT f FROM FileMetadata f
            WHERE f.userId = :userId
              AND f.status <> 'DELETADO'
              AND f.criadoEm < :cursor
            ORDER BY f.criadoEm DESC
            """)
    List<FileMetadata> findByUserIdBeforeCursor(
            @Param("userId") String userId,
            @Param("cursor") Instant cursor,
            Pageable pageable);

    @Query("""
            SELECT f.status, COUNT(f)
            FROM FileMetadata f
            WHERE f.userId = :userId
            GROUP BY f.status
            """)
    List<Object[]> contarPorStatus(@Param("userId") String userId);
}
