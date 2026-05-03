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
public class SimulationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulationController.class);

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
}
