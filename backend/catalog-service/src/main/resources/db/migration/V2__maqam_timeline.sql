-- The maqam journey across a song: [{start, end, maqam, arabic, tonicNote, tonicCents, confidence, uncertain}].
-- Older analyses keep NULL until they are re-analysed.
ALTER TABLE analyses ADD COLUMN maqam_timeline JSONB;
