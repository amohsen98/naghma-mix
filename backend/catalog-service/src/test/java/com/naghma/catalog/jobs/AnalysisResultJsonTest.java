package com.naghma.catalog.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** The worker's result message is a contract: these fields must survive deserialization. */
class AnalysisResultJsonTest {

    @Test
    void maqamJourneyIsRead() throws Exception {
        String json = """
                {"jobId":"5b0c5a9e-1d4e-4c43-9a43-3f6f5b5b1a01","trackId":"5b0c5a9e-1d4e-4c43-9a43-3f6f5b5b1a02",
                 "status":"DONE","bpm":92.0,"somethingNew":1,
                 "maqamTimeline":[
                   {"start":0.0,"end":60.0,"maqam":"Ajam","arabic":"عجم","tonicNote":"F","tonicCents":500.0,
                    "confidence":0.71,"uncertain":false},
                   {"start":60.0,"end":90.0,"maqam":"Sikah","arabic":"سيكاه","tonicNote":"E half-flat",
                    "tonicCents":350.0,"confidence":0.55,"uncertain":false},
                   {"start":90.0,"end":104.2,"maqam":null,"arabic":null,"tonicNote":null,"tonicCents":null,
                    "confidence":0.0,"uncertain":true}]}
                """;
        AnalysisResult r = new ObjectMapper().readValue(json, AnalysisResult.class);
        assertTrue(r.done());
        assertEquals(3, r.maqamTimeline().size());
        assertEquals("Sikah", r.maqamTimeline().get(1).maqam());
        assertEquals(60.0, r.maqamTimeline().get(1).start());
        assertNull(r.maqamTimeline().get(2).maqam());
    }

    @Test
    void olderWorkersWithoutAJourneyStillParse() throws Exception {
        AnalysisResult r = new ObjectMapper().readValue(
                "{\"jobId\":\"5b0c5a9e-1d4e-4c43-9a43-3f6f5b5b1a01\",\"status\":\"DONE\"}", AnalysisResult.class);
        assertNull(r.maqamTimeline());
    }
}
