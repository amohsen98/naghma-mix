# Architecture

```
 browser ──REST──▶ Caddy / nginx ──▶ accounts-service  JWT, plans, billing
    │                            ├─▶ catalog-service   uploads, tracks, tags, corrections
    │                            └─▶ mix-service       scoring, next track, sets, exports
    │                                       │ outbox ─▶ Kafka analysis.jobs ─▶ worker × N
    │                                       ◀── Kafka analysis.results ◀──────────┘
    └──presigned PUT (same origin) ──▶ RustFS / S3 ◀─── worker downloads, then deletes the audio
```

## Request flows

**Upload and analysis**

1. The browser hashes each file (SHA-256) and asks `catalog-service` for upload slots. Files already in the library are reported as duplicates and never uploaded again; the monthly plan quota is checked here.
2. The browser PUTs each file to object storage with a presigned URL valid for 15 minutes. The URL points at the app's own origin (`/naghma-audio/…`), and nginx forwards it to storage unchanged, so the browser needs no CORS rules.
3. The browser confirms. The catalog checks the stored object's size, then marks the track `QUEUED` and writes the job message to the **outbox table in the same transaction**.
4. A scheduled relay sends outbox rows to Kafka (`FOR UPDATE SKIP LOCKED`, so several catalog instances can relay without duplicates).
5. A worker analyzes the file, publishes the result to `analysis.results`, deletes the audio, and only then commits its Kafka offset.
6. The catalog applies the result only if the job is still `PENDING`, so redelivered messages are harmless.

**Next track and sets**

`mix-service` holds no copy of track data. It fetches features from the catalog with the caller's JWT (so it can only see the caller's tracks), behind a timeout, retries and a circuit breaker, and computes scores in memory.

## Maqam detection (v0)

1. Separate the melody: Demucs vocals if installed (`pip install demucs`, `NAGHMA_DEMUCS=1`), otherwise harmonic/percussive separation.
2. Track the melody's pitch with pYIN and keep only stable notes, so glides and vibrato don't blur the scale.
3. Build a pitch-class histogram with 10-cent bins.
4. Correlate it with templates for Rast, Bayati, Saba, Sikah, Hijaz, Nahawand, Kurd and Ajam at all 120 tonic rotations. The best rotation gives the tonic; a softmax over scores gives the top 3 with confidence.
5. Decide Western vs Arabic-modal from the share of notes that sit a quarter tone off the recording's own tuning grid, plus distinctive intervals (Hijaz, Saba). Below the confidence threshold the result is marked **uncertain** instead of guessed.

**Maqam journey.** Arabic songs often modulate: a verse in Ajam, a couplet in Sikah, back to Ajam. The worker pitch-tracks the whole song once (up to 8 minutes), classifies each ~30 s window on its own, and merges neighbouring windows that agree on the maqam and its tonic (within 30 cents). A short tail is folded into the last window. Windows with too little stable melody (rap verses, drum breaks, intros) are stored as "no melody" instead of a guess. The overall label still comes from the middle 90 s. The journey is stored as JSONB in `catalog.analyses.maqam_timeline` (Flyway V2); analyses made before V2 get it on re-analysis.

**Hidden maqam next to the key.** When a track is tagged Western but its top candidates include Nahawand or Kurd (minor key), Ajam (major key) or Hijaz (either) at 30% or more, the app shows it beside the Camelot tag, e.g. `11A F#m ≈ Nahawand`, so the maqam isn't lost behind the key.

Known limits: dense modern productions (autotune, synth pads, mahraganat) are harder than tarab and sha'bi. Maqamat without quarter tones (Nahawand, Kurd, Ajam) are indistinguishable from Western minor/Phrygian/major by pitch alone, so they are tagged Western with the maqam kept as a candidate. Every DJ correction is stored in `catalog.corrections`, to become the labelled dataset for a trained classifier.

## Transition scoring

`S = w_harmony·M + w_tonic·T + w_tempo·B + w_energy·E`, normalized by the weight sum; the DJ can change the weights.

- **M, harmony**: Camelot rules for two Western tracks. For any pair involving a maqam: the share of scale notes the two tracks have in common at their real pitches (quarter tones count), trying a ±1 semitone key-lock shift. The same rule bridges traditions: Bayati on D and D minor share 6 of 7 notes.
- **T, tonic**: interval class, not raw distance. A fourth or fifth is close; a tritone or a quarter-tone offset is far.
- **B, tempo**: BPM gap in percent, half and double time allowed.
- **E, energy**: how close the change in energy is to what the DJ wants at that point.

Reasons come back as codes with arguments (`{"code":"sharedNotes","args":{"shared":6,"of":7,…}}`), so the app words them in Arabic or English.

**Set ordering** is a travelling-salesman variant: greedy construction, then pairwise-swap local search over unlocked positions. The position-independent part of every pair score is computed once, and each swap only re-evaluates the terms it touches, so 200 tracks order in under a second.

## Design decisions

- **Audio never passes through the services.** Browsers PUT to object storage with 15-minute presigned URLs (proxied by nginx on the same origin, never by a Java service). The worker deletes the file after analysis unless the user chose to keep it.
- **Transactional outbox.** Queuing a job writes the track status and the Kafka message in one database transaction. A relay sends it with `FOR UPDATE SKIP LOCKED`, so a crash can't lose a job and several catalog instances can run side by side.
- **At-least-once, idempotent.** The worker commits offsets only after publishing a result; the catalog applies a result only while its job is still pending.
- **Corrections win.** Fields a DJ edits are recorded in `edited_fields` and are never overwritten by re-analysis.
- **Services share a Postgres instance, not tables.** Each owns its schema and talks to the others over HTTP or Kafka, so splitting the databases later only changes connection strings. Oracle can replace PostgreSQL by swapping the JDBC driver and the JSONB column types in the migrations.


## Data ownership

| Schema | Owner | Tables |
|---|---|---|
| `accounts` | accounts-service | `users` |
| `catalog` | catalog-service | `tracks`, `analyses`, `analysis_jobs`, `corrections`, `outbox` |
| `mix` | mix-service | `dj_sets` |

No service reads another service's schema.
