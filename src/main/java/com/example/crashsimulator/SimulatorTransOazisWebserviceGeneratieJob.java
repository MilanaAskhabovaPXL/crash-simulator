package com.example.crashsimulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SimulatorTransOazisWebserviceGeneratieJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorTransOazisWebserviceGeneratieJob.class);

    public void executeSuccess() {
        LOGGER.info("TransOazisWebserviceGeneratieJob started");

        for (int i = 1; i <= 45; i++) {
            LOGGER.info("Processing session " + i + " - Generating XML message");
            generateXMLMessage("SESSION_" + i);
        }

        LOGGER.info("TransOazisWebserviceGeneratieJob completed successfully");
        LOGGER.info("Total sessions processed: 45");
    }

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

    public void executeSilentFailure() {
        LOGGER.info("TransOazisWebserviceGeneratieJob started");
        LOGGER.info("Checking for sessions to process...");
        LOGGER.info("Found 0 sessions in database");
        LOGGER.info("Processing sessions...");
        LOGGER.info("Sending XML messages to Mirth...");
        // simulates silent failure: job reports success despite processing nothing
        LOGGER.info("TransOazisWebserviceGeneratieJob completed successfully");
        LOGGER.info("Total sessions processed: 0");
    }

    private void generateXMLMessage(String sessionId) {
        String xml = "<session id=\"" + sessionId + "\"/>";
        LOGGER.debug("Generated XML for " + sessionId + ": " + xml);
    }

    private void generateXMLMessageWithError() {
        SimulatorTransLayoutConfig config = null;
        String value = config.getLayout().toString();
    }
}
