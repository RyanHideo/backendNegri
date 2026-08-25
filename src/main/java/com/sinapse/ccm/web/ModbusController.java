package com.sinapse.ccm.web;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/modbus")
@CrossOrigin(origins = "*")
public class ModbusController {

    private final ModbusService modbusService;

    /**
     * O caminho ccm1 permanece como alias de compatibilidade para o frontend já implantado.
     */
    @GetMapping({"/ccm/tags", "/ccm1/tags"})
    public Map<String, TagValue> getTags() {
        return modbusService.snapshot();
    }

    @GetMapping({"/ccm/tags/{tagName}", "/ccm1/tags/{tagName}"})
    public ResponseEntity<TagValue> getTag(@org.springframework.web.bind.annotation.PathVariable String tagName) {
        return modbusService.get(tagName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Data
    public static class WriteRequest {
        private String name;
        private int value;
    }

    @PostMapping({"/ccm/write", "/ccm1/write"})
    public ResponseEntity<?> writeTag(@RequestBody WriteRequest request) {
        try {
            modbusService.write(request.getName(), request.getValue());
            return ResponseEntity.ok().build();
        } catch (Exception exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @PostMapping({"/ccm/reset", "/ccm1/reset"})
    public ResponseEntity<?> reset(@RequestParam(defaultValue = "200") int pulseMs) {
        try {
            modbusService.resetPulse(pulseMs);
            return ResponseEntity.ok().build();
        } catch (Exception exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @PostMapping({"/ccm/emergency", "/ccm1/emergency"})
    public ResponseEntity<?> emergency() {
        try {
            modbusService.latchEmergency();
            return ResponseEntity.ok().build();
        } catch (Exception exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }
}
