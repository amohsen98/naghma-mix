package com.naghma.catalog.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Latest analysis of a track, with any DJ corrections applied on top. */
@Entity
@Table(name = "analyses")
public class Analysis extends PanacheEntityBase {

    public record EnergyPoint(double start, double end, double energy) {}

    public record Candidate(String name, String arabic, double confidence) {}

    @Id
    @Column(name = "track_id")
    public UUID trackId;

    @Column(name = "job_id", nullable = false)
    public UUID jobId;

    @Column(name = "model_version")
    public String modelVersion;

    @Column(name = "analyzer_version")
    public String analyzerVersion;

    @Column(name = "duration_sec")
    public Double durationSec;

    public Double bpm;
    public Double lufs;
    public Double energy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "energy_curve")
    public List<EnergyPoint> energyCurve;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "beat_times")
    public List<Double> beatTimes;

    public String tonality;

    @Column(name = "key_tonic_pc")
    public Integer keyTonicPc;

    @Column(name = "key_mode")
    public String keyMode;

    public String camelot;

    @Column(name = "key_confidence")
    public Double keyConfidence;

    public String maqam;

    @Column(name = "maqam_confidence")
    public Double maqamConfidence;

    @Column(name = "maqam_uncertain")
    public Boolean maqamUncertain;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "maqam_candidates")
    public List<Candidate> maqamCandidates;

    @Column(name = "tonic_cents")
    public Double tonicCents;

    @Column(name = "tonic_hz")
    public Double tonicHz;

    @Column(name = "tonic_note")
    public String tonicNote;

    @Column(name = "quarter_tone_ratio")
    public Double quarterToneRatio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pitch_histogram")
    public List<Double> pitchHistogram;

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Integer> timings;

    /** Fields a DJ corrected; re-analysis never overwrites them. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "edited_fields", nullable = false)
    public Set<String> editedFields = Set.of();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
