package com.naghma.catalog.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A tag a DJ changed: labelled data for retraining after expert review. */
@Entity
@Table(name = "corrections")
public class Correction extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "track_id", nullable = false)
    public UUID trackId;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(nullable = false)
    public String field;

    @Column(name = "old_value")
    public String oldValue;

    @Column(name = "new_value")
    public String newValue;

    @Column(name = "model_version")
    public String modelVersion;

    @Column(nullable = false)
    public boolean reviewed;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
