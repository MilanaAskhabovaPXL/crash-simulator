package com.example.crashsimulator;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SimulatorTransOazisWebserviceGeneratieJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorTransOazisWebserviceGeneratieJob.class);
    private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("crash-simulator", "0.0.1");

    public void executeSuccess() {
        Span jobSpan = TRACER.spanBuilder("TransOazisWebserviceGeneratieJob.executeSuccess")
                .setAttribute("job.type", "TransOazisWebserviceGeneratie")
                .setAttribute("job.scenario", "success")
                .startSpan();

        try (Scope jobScope = jobSpan.makeCurrent()) {
            LOGGER.info("TransOazisWebserviceGeneratieJob started");

            for (int i = 1; i <= 45; i++) {
                LOGGER.info("Processing session " + i + " - Generating XML message");
                generateXMLMessage("SESSION_" + i);
            }

            jobSpan.setAttribute("job.sessions_processed", 45);
            jobSpan.setStatus(StatusCode.OK);
            LOGGER.info("TransOazisWebserviceGeneratieJob completed successfully");
            LOGGER.info("Total sessions processed: 45");
        } finally {
            jobSpan.end();
        }
    }

    public void executeCrashLoop(int iterations) {
        Span jobSpan = TRACER.spanBuilder("TransOazisWebserviceGeneratieJob.executeCrashLoop")
                .setAttribute("job.type", "TransOazisWebserviceGeneratie")
                .setAttribute("job.scenario", "crash_loop")
                .setAttribute("job.iterations", iterations)
                .startSpan();

        try (Scope jobScope = jobSpan.makeCurrent()) {
            LOGGER.info("TransOazisWebserviceGeneratieJob started");
            int errorCount = 0;

            for (int i = 1; i <= iterations; i++) {
                Span sessionSpan = TRACER.spanBuilder("generateXMLMessage.session")
                        .setAttribute("session.index", i)
                        .startSpan();

                try (Scope sessionScope = sessionSpan.makeCurrent()) {
                    LOGGER.info("Processing session " + i);
                    generateXMLMessageWithError();
                } catch (NullPointerException e) {
                    errorCount++;
                    sessionSpan.setStatus(StatusCode.ERROR, "NullPointerException: transLayoutConfig is null");
                    sessionSpan.recordException(e);
                    LOGGER.error("NullPointerException in getXMLData(): transLayoutConfig is null for session " + i, e);
                } finally {
                    sessionSpan.end();
                }
            }

            jobSpan.setAttribute("job.error_count", errorCount);
            jobSpan.setStatus(StatusCode.ERROR, "Job failed with " + iterations + " errors");
            LOGGER.error("TransOazisWebserviceGeneratieJob failed: " + iterations + " errors occurred");
        } finally {
            jobSpan.end();
        }
    }

    public void executeSilentFailure() {
        Span jobSpan = TRACER.spanBuilder("TransOazisWebserviceGeneratieJob.executeSilentFailure")
                .setAttribute("job.type", "TransOazisWebserviceGeneratie")
                .setAttribute("job.scenario", "silent_failure")
                .startSpan();

        try (Scope jobScope = jobSpan.makeCurrent()) {
            LOGGER.info("TransOazisWebserviceGeneratieJob started");
            LOGGER.info("Checking for sessions to process...");
            LOGGER.info("Found 0 sessions in database");
            LOGGER.info("Processing sessions...");
            LOGGER.info("Sending XML messages to Mirth...");

            // span markeert dat 0 sessies verwerkt werden, ondanks "success" status in log
            jobSpan.setAttribute("job.sessions_processed", 0);
            jobSpan.setAttribute("job.silent_failure", true);
            jobSpan.setStatus(StatusCode.OK);

            LOGGER.info("TransOazisWebserviceGeneratieJob completed successfully");
            LOGGER.info("Total sessions processed: 0");
        } finally {
            jobSpan.end();
        }
    }

    private void generateXMLMessage(String sessionId) {
        Span span = TRACER.spanBuilder("generateXMLMessage")
                .setAttribute("session.id", sessionId)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            String xml = "<session id=\"" + sessionId + "\"/>";
            span.setAttribute("xml.length", xml.length());
            LOGGER.debug("Generated XML for " + sessionId + ": " + xml);
        } finally {
            span.end();
        }
    }

    private void generateXMLMessageWithError() {
        SimulatorTransLayoutConfig config = null;
        String value = config.getLayout().toString();
    }
}
