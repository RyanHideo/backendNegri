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
}
