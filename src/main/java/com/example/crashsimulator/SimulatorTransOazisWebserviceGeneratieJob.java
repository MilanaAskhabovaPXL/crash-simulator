package com.example.crashsimulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SimulatorTransOazisWebserviceGeneratieJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorTransOazisWebserviceGeneratieJob.class);

    /**
     * SCENARIO 1: Normale uitvoering (succes)
     * Simuleert verwerking van 45 sessies zonder fouten.
     */
    public void executeSuccess() {
        LOGGER.info("TransOazisWebserviceGeneratieJob started");

        for (int i = 1; i <= 45; i++) {
            LOGGER.info("Processing session " + i + " - Generating XML message");
            generateXMLMessage("SESSION_" + i);
        }

        LOGGER.info("TransOazisWebserviceGeneratieJob completed successfully");
        LOGGER.info("Total sessions processed: 45");
    }

    /**
     * SCENARIO 2: Crash-loop (NullPointerException)
     * transLayoutConfig is null → NullPointerException per sessie.
     */
    public void executeCrashLoop(int iterations) {
        LOGGER.info("TransOazisWebserviceGeneratieJob started");

        for (int i = 1; i <= iterations; i++) {
            try {
                LOGGER.info("Processing session " + i);
                generateXMLMessageWithError();
            } catch (NullPointerException e) {
                LOGGER.error("NullPointerException in getXMLData(): transLayoutConfig is null for session " + i, e);
            }
        }

        LOGGER.error("TransOazisWebserviceGeneratieJob failed: " + iterations + " errors occurred");
    }

    /**
     * SCENARIO 3: Silent Failure (0 records verwerkt)
     * Database-query retourneert 0 sessies maar de job logt succes.
     */
    public void executeSilentFailure() {
        LOGGER.info("TransOazisWebserviceGeneratieJob started");
        LOGGER.info("Checking for sessions to process...");

        int sessiesGevonden = 0;
        LOGGER.info("Found " + sessiesGevonden + " sessions in database");

        LOGGER.info("Processing sessions...");
        LOGGER.info("Sending XML messages to Mirth...");

        // Geen loop: 0 sessies → geen XML gegenereerd, maar job meldt succes
        LOGGER.info("TransOazisWebserviceGeneratieJob completed successfully");
        LOGGER.info("Total sessions processed: 0");
    }

    // ==================== Helper Methods ====================

    private void generateXMLMessage(String sessionId) {
        String xml = "<session id=\"" + sessionId + "\"/>";
        LOGGER.debug("Generated XML for " + sessionId + ": " + xml);
    }

    private void generateXMLMessageWithError() {
        SimulatorTransLayoutConfig config = null;  // ← Intentional null (simulates transLayoutConfig == null)
        // Throws NullPointerException — equivalent to TransWriterHelper.getXMLData()
        String value = config.getLayout().toString();
    }
}
