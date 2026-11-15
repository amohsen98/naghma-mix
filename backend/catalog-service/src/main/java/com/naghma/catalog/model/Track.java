package com.naghma.catalog.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "tracks")
public class Track extends PanacheEntityBase {

    public enum Status { AWAITING_UPLOAD, QUEUED, DONE, FAILED }

    @Id
    public UUID id;

    @Column(name = "owner_id", nullable = false)
    public UUID ownerId;

    @Column(nullable = false)
    public String title;

    public String artist;

    @Column(name = "original_filename", nullable = false)
    public String originalFilename;

    @Column(name = "content_hash", nullable = false, length = 64)
    public String contentHash;

    @Column(name = "size_bytes", nullable = false)
    public long sizeBytes;

    @Column(name = "content_type", nullable = false)
    public String contentType;

    @Column(name = "object_key", nullable = false)
    public String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Status status;

    public String error;

    @Column(name = "keep_audio", nullable = false)
    public boolean keepAudio;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "analyzed_at")
    public Instant analyzedAt;

    public static Optional<Track> owned(UUID id, UUID owner) {
        return find("id = ?1 and ownerId = ?2", id, owner).firstResultOptional();
    }

    public static Optional<Track> byHash(UUID owner, String hash) {
        return find("ownerId = ?1 and contentHash = ?2", owner, hash).firstResultOptional();
    }

    public static long createdSince(UUID owner, Instant since) {
        return count("ownerId = ?1 and createdAt >= ?2", owner, since);
    }
}
