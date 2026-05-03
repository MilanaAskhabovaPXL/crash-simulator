package com.example.crashsimulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/simulate")
public class SimulatorSimulationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorSimulationController.class);

    /**
     * SCENARIO 1: Expliciet Exception (Crash-loop)
     * NullPointerException wordt N keer geworpen en gelogd.
     */
    @PostMapping("/crash")
    public ResponseEntity<String> simulateCrash(@RequestParam(defaultValue = "1") int count) {
        for (int i = 0; i < count; i++) {
            try {
                Map<String, Object> config = null;  // ← Intentional null
                String value = config.get("key").toString();  // ← NullPointerException
            } catch (NullPointerException e) {
                LOGGER.error("NullPointerException in getXMLData(): transLayoutConfig is null", e);
            }
        }
        return ResponseEntity.ok("Crash simulation complete. Check logs.");
    }

    /**
     * SCENARIO 2: Trage response / latency spike
     * Simuleert een hangende of trage externe call (bijv. database/API timeout).
     * De thread slaapt 'delayMs' milliseconden en logt een waarschuwing.
     *
     * Gebruik:
     *   curl -X POST http://localhost:8080/simulate/slow
     *   curl -X POST "http://localhost:8080/simulate/slow?delayMs=5000"
     */
    @PostMapping("/slow")
    public ResponseEntity<String> simulateSlow(@RequestParam(defaultValue = "3000") long delayMs) {
        LOGGER.warn("Slow call started: simulating delay of {} ms", delayMs);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Slow call interrupted after {} ms", delayMs, e);
            return ResponseEntity.status(500).body("Slow simulation interrupted.");
        }
        LOGGER.warn("Slow call finished after {} ms", delayMs);
        return ResponseEntity.ok("Slow simulation complete (" + delayMs + " ms delay). Check logs.");
    }
}
