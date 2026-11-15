package com.naghma.mix.sets;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A saved set: the ordered track ids plus which positions the DJ locked. */
@Entity
@Table(name = "dj_sets")
public class DjSet extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "owner_id", nullable = false)
    public UUID ownerId;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String curve;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "track_ids", nullable = false)
    public List<UUID> trackIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "locked_positions", nullable = false)
    public List<Integer> lockedPositions;

    @Column(name = "w_harmony", nullable = false)
    public double wHarmony;

    @Column(name = "w_tonic", nullable = false)
    public double wTonic;

    @Column(name = "w_tempo", nullable = false)
    public double wTempo;

    @Column(name = "w_energy", nullable = false)
    public double wEnergy;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    public static List<DjSet> forOwner(UUID owner) {
        return list("ownerId = ?1 order by updatedAt desc", owner);
    }
}
