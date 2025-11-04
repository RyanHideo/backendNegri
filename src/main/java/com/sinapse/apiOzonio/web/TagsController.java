package com.sinapse.apiOzonio.web;

import com.sinapse.apiOzonio.modbus.ModbusService;
import com.sinapse.apiOzonio.modbus.TagValue;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TagsController {

    private final ModbusService modbus;

    // Executor dedicado apenas para desligar pulsos sem travar a thread HTTP
    private final ScheduledExecutorService pulseExec = Executors.newSingleThreadScheduledExecutor();

    /** ======================== LISTAGEM DE TAGS ======================== */
    @GetMapping("/tags")
    public Map<String, Object> list() {
        return Map.of("ts", Instant.now().toString(), "tags", modbus.snapshot());
    }

    /** ======================== LEITURA INDIVIDUAL ======================== */
    @GetMapping("/tags/{name}")
    public ResponseEntity<TagValue> get(@PathVariable String name) {
        return modbus.get(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** ======================== ESCRITA GENÉRICA ======================== */
    record WriteRequest(int value) {}

    @PostMapping("/tags/{name}")
    public ResponseEntity<?> write(@PathVariable String name, @RequestBody WriteRequest body) {
        try {
            modbus.write(name, body.value());
            return ResponseEntity.accepted().body(Map.of("status", "OK"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** ======================== COMANDOS ======================== */

    /**
     * RESET — pulso não retentivo
     * O CLP deve detectar a borda de subida e realizar a lógica.
     */
    @PostMapping("/cmd/reset")
    public ResponseEntity<?> cmdReset(@RequestParam(defaultValue = "200") int pulseMs) {
        try {
            pulseNonBlocking("RESET", pulseMs);
            return ResponseEntity.accepted()
                    .body(Map.of("status", "RESET pulsed (non-blocking)", "ms", pulseMs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * EMERGÊNCIA — pulso retentivo (o latch é feito no CLP).
     * Coil 1 = comando, Coil 9 = feedback da parada.
     */
    @PostMapping("/cmd/emergencia")
    public ResponseEntity<?> cmdEmergencia(@RequestParam(defaultValue = "200") int pulseMs) {
        try {
            pulseNonBlocking("EMERGENCIA", pulseMs);
            return ResponseEntity.accepted()
                    .body(Map.of("status", "EMERGENCIA pulsed (non-blocking)", "ms", pulseMs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** ======================== FUNÇÃO AUXILIAR ======================== */
    private void pulseNonBlocking(String tag, int pulseMs) throws Exception {
        // Liga o coil imediatamente
        modbus.write(tag, 1);
        // Agenda o desligamento sem travar a requisição
        pulseExec.schedule(() -> {
            try {
                modbus.write(tag, 0);
            } catch (Exception e) {
                System.err.println("Falha ao desligar pulso de " + tag + ": " + e.getMessage());
            }
        }, pulseMs, TimeUnit.MILLISECONDS);
    }
}
