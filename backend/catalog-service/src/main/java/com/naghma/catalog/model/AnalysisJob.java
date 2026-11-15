package com.naghma.catalog.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One analysis request. Its id makes result handling idempotent. */
@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob extends PanacheEntityBase {

    public enum Status { PENDING, DONE, FAILED }

    @Id
    public UUID id;

    @Column(name = "track_id", nullable = false)
    public UUID trackId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Status status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "finished_at")
    public Instant finishedAt;
}
