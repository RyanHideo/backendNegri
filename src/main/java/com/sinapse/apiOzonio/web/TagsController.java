package com.sinapse.apiOzonio.web;

import com.sinapse.apiOzonio.modbus.ModbusService;
import com.sinapse.apiOzonio.modbus.TagValue;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TagsController {
    private final ModbusService modbus;

    @GetMapping("/tags")
    public Map<String, Object> list() {
        return Map.of("ts", Instant.now().toString(), "tags", modbus.snapshot());
    }

    @GetMapping("/tags/{name}")
    public ResponseEntity<TagValue> get(@PathVariable String name) {
        return modbus.get(name).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    record WriteRequest(int value) {}

    @PostMapping("/tags/{name}")
    public ResponseEntity<?> write(@PathVariable String name, @RequestBody WriteRequest body) {
        try {
            modbus.write(name, body.value());
            return ResponseEntity.accepted().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    @PostMapping("/cmd/reset")
    public ResponseEntity<?> cmdReset(@RequestParam(defaultValue = "200") int pulseMs) {
        try {
            modbus.pulse("RESET", pulseMs); // não-retentivo
            return ResponseEntity.accepted().body(Map.of("status", "RESET pulsed", "ms", pulseMs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * EMERGÊNCIA deve ser retentiva (latch no CLP).
     * Aqui só ligamos. O destrave deve ocorrer pelo circuito de RESET no CLP.
     */
    @PostMapping("/cmd/emergencia")
    public ResponseEntity<?> cmdEmergencia() {
        try {
            modbus.write("EMERGENCIA", 1); // retentivo - não desliga aqui
            return ResponseEntity.accepted().body(Map.of("status", "EMERGENCIA ON"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
