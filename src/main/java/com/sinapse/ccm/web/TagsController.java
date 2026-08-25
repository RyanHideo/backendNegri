package com.sinapse.ccm.web;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagCatalog;
import com.sinapse.ccm.modbus.TagDef;
import com.sinapse.ccm.modbus.TagValue;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173"})
public class TagsController {

    private static final String LEGACY_CCM_KEY = "ccm1";

    private final ModbusService modbus;
    private final TagCatalog ccmCatalog;
    private final ScheduledExecutorService pulseExec = Executors.newSingleThreadScheduledExecutor();

    @GetMapping("/tags/definitions")
    public Collection<TagDef> getDefinitions() {
        return ccmCatalog.getTags();
    }

    @GetMapping("/tags")
    public Map<String, Object> list() {
        return Map.of(
                "ts", Instant.now().toString(),
                "tags", modbus.snapshot()
        );
    }

    @GetMapping("/tags/{name}")
    public ResponseEntity<TagValue> get(@PathVariable String name) {
        Optional<TagValue> value = modbus.get(name);
        return value.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record WriteRequest(int value) {
    }

    @PostMapping("/tags/{name}")
    public ResponseEntity<?> write(@PathVariable String name, @RequestBody WriteRequest body) {
        try {
            modbus.write(name, body.value());
            return ResponseEntity.accepted().body(Map.of("status", "OK"));
        } catch (Exception exception) {
            log.warn("Falha ao escrever tag {}: {}", name, exception.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/cmd/reset")
    public ResponseEntity<?> cmdReset(
            @RequestParam(name = "pulseMs", required = false) Integer pulseMs
    ) {
        int milliseconds = pulseMs == null || pulseMs <= 0 ? 400 : pulseMs;

        try {
            pulseNonBlocking("RESET", milliseconds);
            return ResponseEntity.accepted().body(Map.of(
                    "status", "RESET pulsed",
                    "ms", milliseconds,
                    "ccm", LEGACY_CCM_KEY
            ));
        } catch (Exception exception) {
            log.warn("Falha ao pulsar RESET: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/cmd/parar")
    public ResponseEntity<?> cmdParar() {
        try {
            modbus.latchEmergency();
            return ResponseEntity.ok(Map.of(
                    "status", "EMERGENCIA setada (retentivo)",
                    "ccm", LEGACY_CCM_KEY
            ));
        } catch (Exception exception) {
            log.warn("Falha ao acionar EMERGENCIA: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/cmd/parar/clear")
    public ResponseEntity<?> cmdPararClear() {
        try {
            modbus.clearEmergency();
            return ResponseEntity.ok(Map.of(
                    "status", "EMERGENCIA limpa",
                    "ccm", LEGACY_CCM_KEY
            ));
        } catch (Exception exception) {
            log.warn("Falha ao limpar EMERGENCIA: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    private void pulseNonBlocking(String tag, int pulseMs) throws Exception {
        modbus.write(tag, 1);
        pulseExec.schedule(() -> {
            try {
                modbus.write(tag, 0);
            } catch (Exception exception) {
                log.warn("Falha ao desligar pulso de {}: {}", tag, exception.getMessage());
            }
        }, pulseMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        pulseExec.shutdownNow();
    }
}
