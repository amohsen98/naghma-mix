package com.naghma.mix.domain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Playlist files DJ software can import (FR-5.3). */
public final class PlaylistExport {

    private PlaylistExport() {}

    /**
     * Extended M3U. Audio is deleted after analysis, so locations are the original file
     * names; DJ software resolves them against the DJ's own music folder.
     */
    public static String m3u(String setName, List<TrackFeatures> order) {
        StringBuilder sb = new StringBuilder("#EXTM3U\n#PLAYLIST:").append(oneLine(setName)).append('\n');
        for (TrackFeatures t : order) {
            long secs = t.durationSec() == null ? -1 : Math.round(t.durationSec());
            sb.append("#EXTINF:").append(secs).append(',').append(oneLine(t.label())).append('\n');
            sb.append(t.filename() == null ? oneLine(t.title()) : t.filename()).append('\n');
        }
        return sb.toString();
    }

    /** rekordbox collection XML with one playlist; keys in Camelot-free musical notation. */
    public static String rekordboxXml(String setName, List<TrackFeatures> order) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<DJ_PLAYLISTS Version=\"1.0.0\">\n");
        sb.append("  <PRODUCT Name=\"Naghma Mix\" Version=\"0.1.0\" Company=\"Naghma\"/>\n");
        sb.append("  <COLLECTION Entries=\"").append(order.size()).append("\">\n");
        for (int i = 0; i < order.size(); i++) {
            TrackFeatures t = order.get(i);
            sb.append("    <TRACK TrackID=\"").append(i + 1).append('"')
                    .append(attr("Name", t.title()))
                    .append(attr("Artist", t.artist()))
                    .append(attr("TotalTime", t.durationSec() == null ? null : String.valueOf(Math.round(t.durationSec()))))
                    .append(attr("AverageBpm", t.bpm() == null ? null : String.format(java.util.Locale.ROOT, "%.2f", t.bpm())))
                    .append(attr("Tonality", rekordboxKey(t)))
                    .append(attr("Comments", t.isArabic() ? "Maqam " + Scales.describe(t) : null))
                    .append(attr("Location", t.filename() == null ? null
                            : "file://localhost/" + URLEncoder.encode(t.filename(), StandardCharsets.UTF_8).replace("+", "%20")))
                    .append("/>\n");
        }
        sb.append("  </COLLECTION>\n");
        sb.append("  <PLAYLISTS>\n");
        sb.append("    <NODE Type=\"0\" Name=\"ROOT\" Count=\"1\">\n");
        sb.append("      <NODE").append(attr("Name", setName)).append(" Type=\"1\" KeyType=\"0\" Entries=\"")
                .append(order.size()).append("\">\n");
        for (int i = 0; i < order.size(); i++) {
            sb.append("        <TRACK Key=\"").append(i + 1).append("\"/>\n");
        }
        sb.append("      </NODE>\n    </NODE>\n  </PLAYLISTS>\n</DJ_PLAYLISTS>\n");
        return sb.toString();
    }

    static String rekordboxKey(TrackFeatures t) {
        if (!t.hasWesternKey()) {
            return null;
        }
        String[] names = {"C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"};
        String n = names[Math.floorMod(t.keyTonicPc(), 12)];
        return "major".equals(t.keyMode()) ? n : n + "m";
    }

    private static String attr(String name, String value) {
        return value == null ? "" : " " + name + "=\"" + xml(value) + "\"";
    }

    static String xml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    if (c >= 0x20 || c == '\t') {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n]+", " ");
    }
}
