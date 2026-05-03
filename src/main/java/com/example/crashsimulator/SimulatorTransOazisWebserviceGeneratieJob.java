package com.example.crashsimulator;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SimulatorTransOazisWebserviceGeneratieJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorTransOazisWebserviceGeneratieJob.class);

    private final Tracer tracer;

    public SimulatorTransOazisWebserviceGeneratieJob(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("crash-simulator");
    }

    /**
     * SCENARIO 1: Normale uitvoering — 45 sessies zonder fouten.
     * curl -X POST http://localhost:8080/batch/run-success
     */
    public void executeSuccess() {
        Span rootSpan = tracer.spanBuilder("TransOazisWebserviceGeneratieJob.executeSuccess")
                .setAttribute("scenario", "success")
                .startSpan();

        try (var scope = rootSpan.makeCurrent()) {
            LOGGER.info("TransOazisWebserviceGeneratieJob started");

            for (int i = 1; i <= 45; i++) {
                Span sessionSpan = tracer.spanBuilder("ProcessSession")
                        .setAttribute("sessionId", i)
                        .startSpan();
                try (var sessionScope = sessionSpan.makeCurrent()) {
                    LOGGER.info("Processing session " + i + " - Generating XML message");
                    generateXMLMessage("SESSION_" + i);
                } finally {
                    sessionSpan.end();
                }
            }

            LOGGER.info("TransOazisWebserviceGeneratieJob completed successfully");
            LOGGER.info("Total sessions processed: 45");
            rootSpan.setStatus(StatusCode.OK);
        } finally {
            rootSpan.end();
        }
    }

    /**
     * SCENARIO 2: Crash-loop — NullPointerException per sessie.
     * curl -X POST "http://localhost:8080/batch/run-crash?iterations=600"
     */
    public void executeCrashLoop(int iterations) {
        Span rootSpan = tracer.spanBuilder("TransOazisWebserviceGeneratieJob.executeCrashLoop")
                .setAttribute("scenario", "crash-loop")
                .setAttribute("iterations", iterations)
                .startSpan();

        try (var scope = rootSpan.makeCurrent()) {
            LOGGER.info("TransOazisWebserviceGeneratieJob started");

            for (int i = 1; i <= iterations; i++) {
                Span sessionSpan = tracer.spanBuilder("ProcessSession")
                        .setAttribute("sessionId", i)
                        .startSpan();

                try (var sessionScope = sessionSpan.makeCurrent()) {
                    LOGGER.info("Processing session " + i);
                    generateXMLMessageWithError();
                } catch (NullPointerException e) {
                    sessionSpan.recordException(e);
                    sessionSpan.setStatus(StatusCode.ERROR, "NullPointerException: transLayoutConfig is null");
                    LOGGER.error("NullPointerException in getXMLData(): transLayoutConfig is null for session " + i, e);
                } finally {
                    sessionSpan.end();
                }
            }

            LOGGER.error("TransOazisWebserviceGeneratieJob failed: " + iterations + " errors occurred");
            rootSpan.setStatus(StatusCode.ERROR, iterations + " sessions failed");
        } finally {
            rootSpan.end();
        }
    }

    /**
     * SCENARIO 3: Silent Failure — 0 records verwerkt maar job meldt succes.
     * curl -X POST http://localhost:8080/batch/run-silent-failure
     */
    public void executeSilentFailure() {
        Span rootSpan = tracer.spanBuilder("TransOazisWebserviceGeneratieJob.executeSilentFailure")
                .setAttribute("scenario", "silent-failure")
                .startSpan();

        try (var scope = rootSpan.makeCurrent()) {
            LOGGER.info("TransOazisWebserviceGeneratieJob started");
            LOGGER.info("Checking for sessions to process...");

            int sessiesGevonden = 0;
            rootSpan.setAttribute("sessionsFound", sessiesGevonden);
            LOGGER.info("Found " + sessiesGevonden + " sessions in database");

            LOGGER.info("Processing sessions...");
            LOGGER.info("Sending XML messages to Mirth...");

            // 0 sessies -> geen XML gegenereerd, maar job meldt succes (silent failure)
            LOGGER.info("TransOazisWebserviceGeneratieJob completed successfully");
            LOGGER.info("Total sessions processed: 0");
            rootSpan.setStatus(StatusCode.OK);
        } finally {
            rootSpan.end();
        }
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
