package com.sinapse.apiOzonio.web;

import com.sinapse.apiOzonio.modbus.ModbusService;
import com.sinapse.apiOzonio.modbus.TagValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000"}) // ajuste conforme seu front
public class TagsController {

    private final ModbusService modbus;

    // Executor dedicado para desligar pulsos sem travar a thread HTTP
    private final ScheduledExecutorService pulseExec = Executors.newSingleThreadScheduledExecutor();

    /* ======================== LISTAGEM / LEITURA ======================== */

    @GetMapping("/tags")
    public Map<String, Object> list() {
        // snapshot() já retorna o cache que o ModbusService preenche no polling
        return Map.of(
                "ts", Instant.now().toString(),
                "tags", modbus.snapshot()
        );
    }

    @GetMapping("/tags/{name}")
    public ResponseEntity<TagValue> get(@PathVariable String name) {
        Optional<TagValue> v = modbus.get(name);
        return v.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /* ======================== ESCRITA GENÉRICA ======================== */

    public record WriteRequest(int value) {}

    @PostMapping("/tags/{name}")
    public ResponseEntity<?> write(@PathVariable String name, @RequestBody WriteRequest body) {
        try {
            modbus.write(name, body.value());
            return ResponseEntity.accepted().body(Map.of("status", "OK"));
        } catch (Exception e) {
            log.warn("Falha ao escrever tag {}: {}", name, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    /* ======================== COMANDOS ======================== */

    /**
     * RESET — pulso não retentivo. O CLP deve reagir à borda de subida.
     * Ex.: POST /api/cmd/reset              -> 400 ms (default)
     *      POST /api/cmd/reset?pulseMs=250  -> 250 ms
     */
    @PostMapping("/cmd/reset")
    public ResponseEntity<?> cmdReset(@RequestParam(name = "pulseMs", required = false) Integer pulseMs) {
        int ms = (pulseMs == null || pulseMs <= 0) ? 400 : pulseMs;
        try {
            pulseNonBlocking("RESET", ms);
            return ResponseEntity.accepted().body(Map.of("status", "RESET pulsed", "ms", ms));
        } catch (Exception e) {
            log.warn("Falha ao pulsar RESET: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PARAR/EMERGÊNCIA — RETENTIVO.
     * Seta o coil e NÃO desliga aqui (o latch/deslatch é responsabilidade do CLP).
     * Front chama este endpoint para "Parar".
     */
    @PostMapping("/cmd/parar")
    public ResponseEntity<?> cmdParar() {
        try {
            modbus.write("EMERGENCIA", 1); // coil retentivo
            return ResponseEntity.ok(Map.of("status", "EMERGENCIA setada (retentivo)"));
        } catch (Exception e) {
            log.warn("Falha ao acionar EMERGENCIA: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * (Opcional) Limpa manualmente a EMERGÊNCIA (deslatch).
     * Útil para testes ou fallback caso precise limpar sem acionar o reset geral.
     */
    @PostMapping("/cmd/parar/clear")
    public ResponseEntity<?> cmdPararClear() {
        try {
            modbus.write("EMERGENCIA", 0); // desarma coil
            return ResponseEntity.ok(Map.of("status", "EMERGENCIA limpa"));
        } catch (Exception e) {
            log.warn("Falha ao limpar EMERGENCIA: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ======================== AUXILIARES ======================== */

    /** Pulsa uma tag (liga e agenda desligar) sem travar a requisição HTTP. */
    private void pulseNonBlocking(String tag, int pulseMs) throws Exception {
        modbus.write(tag, 1);
        pulseExec.schedule(() -> {
            try {
                modbus.write(tag, 0);
            } catch (Exception e) {
                log.warn("Falha ao desligar pulso de {}: {}", tag, e.getMessage());
            }
        }, pulseMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        pulseExec.shutdownNow();
    }
}
