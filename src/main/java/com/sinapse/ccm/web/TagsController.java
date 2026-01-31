package com.sinapse.ccm.web;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagCatalog;
import com.sinapse.ccm.modbus.TagDef;
import com.sinapse.ccm.modbus.TagValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173"}) // ajuste conforme seu front
public class TagsController {

    private final ModbusService modbus;
    private final TagCatalog ccm1Catalog; // Injetado para servir as definições
    private final TagCatalog ccm2Catalog; // Injetado para servir as definições

    // Executor dedicado para desligar pulsos sem travar a thread HTTP
    private final ScheduledExecutorService pulseExec = Executors.newSingleThreadScheduledExecutor();

    /** Helper: garante sempre um CCM válido, default = "ccm1" */
    private String normalizeCcm(String raw) {
        if (raw == null || raw.isBlank()) return "ccm1";
        String s = raw.trim().toLowerCase();
        return (s.equals("ccm2")) ? "ccm2" : "ccm1";
    }

    /* ======================== DEFINIÇÕES E LISTAGEM ======================== */

    /**
     * Novo endpoint para servir as definições das tags, incluindo o 'name_front'.
     * O front-end pode chamar este endpoint uma vez para obter os metadados.
     */
    @GetMapping("/tags/definitions")
    public Collection<TagDef> getDefinitions(
            @RequestParam(name = "ccm", required = false) String ccm
    ) {
        String ccmKey = normalizeCcm(ccm);
        if ("ccm2".equals(ccmKey)) {
            return ccm2Catalog.getTags();
        }
        return ccm1Catalog.getTags();
    }

    @GetMapping("/tags")
    public Map<String, Object> list(
            @RequestParam(name = "ccm", required = false) String ccm
    ) {
        String ccmKey = normalizeCcm(ccm);

        return Map.of(
                "ts", Instant.now().toString(),
                "tags", modbus.snapshot(ccmKey)
        );
    }

    /**
     * Endpoint unificado que retorna todas as tags de todos os CCMs em um único mapa plano.
     * As chaves são renomeadas para evitar colisão: "TAG_NAME" -> "TAG_NAME__ccm1"
     */
    @GetMapping("/tags/unified")
    public Map<String, Object> listUnified() {
        Map<String, TagValue> unifiedTags = new HashMap<>();

        // Processa CCM1
        Map<String, TagValue> ccm1Tags = modbus.snapshot("ccm1");
        ccm1Tags.forEach((key, value) -> {
            String newKey = key + "__ccm1";
            unifiedTags.put(newKey, value);
        });

        // Processa CCM2
        Map<String, TagValue> ccm2Tags = modbus.snapshot("ccm2");
        ccm2Tags.forEach((key, value) -> {
            String newKey = key + "__ccm2";
            unifiedTags.put(newKey, value);
        });

        return Map.of(
                "ts", Instant.now().toString(),
                "tags", unifiedTags
        );
    }

    @GetMapping("/tags/{name}")
    public ResponseEntity<TagValue> get(
            @PathVariable String name,
            @RequestParam(name = "ccm", required = false) String ccm
    ) {
        String ccmKey = normalizeCcm(ccm);
        Optional<TagValue> v = modbus.get(ccmKey, name);
        return v.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /* ======================== ESCRITA GENÉRICA ======================== */

    public record WriteRequest(int value) {}

    @PostMapping("/tags/{name}")
    public ResponseEntity<?> write(
            @PathVariable String name,
            @RequestBody WriteRequest body,
            @RequestParam(name = "ccm", required = false) String ccm
    ) {
        String ccmKey = normalizeCcm(ccm);

        try {
            modbus.write(ccmKey, name, body.value());
            return ResponseEntity.accepted().body(Map.of("status", "OK"));
        } catch (Exception e) {
            log.warn("Falha ao escrever tag {} em {}: {}", name, ccmKey, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ======================== COMANDOS ======================== */

    /**
     * RESET — pulso não retentivo. O CLP deve reagir à borda de subida.
     * Ex.: POST /api/cmd/reset?ccm=ccm1          -> 400 ms (default)
     *      POST /api/cmd/reset?ccm=ccm2&pulseMs=250  -> 250 ms
     */
    @PostMapping("/cmd/reset")
    public ResponseEntity<?> cmdReset(
            @RequestParam(name = "pulseMs", required = false) Integer pulseMs,
            @RequestParam(name = "ccm", required = false) String ccm
    ) {
        String ccmKey = normalizeCcm(ccm);
        int ms = (pulseMs == null || pulseMs <= 0) ? 400 : pulseMs;

        try {
            pulseNonBlocking(ccmKey, "RESET", ms);
            return ResponseEntity.accepted()
                    .body(Map.of(
                            "status", "RESET pulsed",
                            "ms", ms,
                            "ccm", ccmKey
                    ));
        } catch (Exception e) {
            log.warn("Falha ao pulsar RESET em {}: {}", ccmKey, e.getMessage());
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
    public ResponseEntity<?> cmdParar(
            @RequestParam(name = "ccm", required = false) String ccm
    ) {
        String ccmKey = normalizeCcm(ccm);
        try {
            modbus.write(ccmKey, "EMERGENCIA", 1); // coil retentivo
            return ResponseEntity.ok(
                    Map.of(
                            "status", "EMERGENCIA setada (retentivo)",
                            "ccm", ccmKey
                    )
            );
        } catch (Exception e) {
            log.warn("Falha ao acionar EMERGENCIA em {}: {}", ccmKey, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * (Opcional) Limpa manualmente a EMERGÊNCIA (deslatch).
     * Útil para testes ou fallback caso precise limpar sem acionar o reset geral.
     */
    @PostMapping("/cmd/parar/clear")
    public ResponseEntity<?> cmdPararClear(
            @RequestParam(name = "ccm", required = false) String ccm
    ) {
        String ccmKey = normalizeCcm(ccm);
        try {
            modbus.write(ccmKey, "EMERGENCIA", 0); // desarma coil
            return ResponseEntity.ok(
                    Map.of(
                            "status", "EMERGENCIA limpa",
                            "ccm", ccmKey
                    )
            );
        } catch (Exception e) {
            log.warn("Falha ao limpar EMERGENCIA em {}: {}", ccmKey, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ======================== AUXILIARES ======================== */

    /** Pulsa uma tag (liga e agenda desligar) sem travar a requisição HTTP. */
    private void pulseNonBlocking(String ccmKey, String tag, int pulseMs) throws Exception {
        modbus.write(ccmKey, tag, 1);
        pulseExec.schedule(() -> {
            try {
                modbus.write(ccmKey, tag, 0);
            } catch (Exception e) {
                log.warn("Falha ao desligar pulso de {} em {}: {}", tag, ccmKey, e.getMessage());
            }
        }, pulseMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        pulseExec.shutdownNow();
    }
}
