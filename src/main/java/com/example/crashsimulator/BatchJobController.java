package com.example.crashsimulator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/batch")
public class BatchJobController {

    @Autowired
    private TransOazisWebserviceGeneratieJob batchJob;

    /**
     * SCENARIO 1: Normale uitvoering
     * curl -X POST http://localhost:8080/batch/run-success
     */
    @PostMapping("/run-success")
    public ResponseEntity<String> runSuccess() {
        batchJob.executeSuccess();
        return ResponseEntity.ok("Batch job executed successfully");
    }

    /**
     * SCENARIO 2: Crash-loop
     * curl -X POST "http://localhost:8080/batch/run-crash?iterations=600"
     */
    @PostMapping("/run-crash")
    public ResponseEntity<String> runCrash(@RequestParam(defaultValue = "600") int iterations) {
        batchJob.executeCrashLoop(iterations);
        return ResponseEntity.ok("Crash simulation executed (" + iterations + " errors)");
    }

    /**
     * SCENARIO 3: Silent Failure
     * curl -X POST http://localhost:8080/batch/run-silent-failure
     */
    @PostMapping("/run-silent-failure")
    public ResponseEntity<String> runSilentFailure() {
        batchJob.executeSilentFailure();
        return ResponseEntity.ok("Silent failure simulation executed");
    }
}
