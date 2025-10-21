package com.sinapse.apiOzonio.web;

import com.sinapse.apiOzonio.modbus.ModbusService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StreamController {
    private final ModbusService modbus;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-cache");
        var emitter = new SseEmitter(0L);
        var exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(() -> {
            try { emitter.send(modbus.snapshot()); }
            catch (Exception e) { emitter.complete(); exec.shutdownNow(); }
        }, 0, 1, TimeUnit.SECONDS);
        emitter.onCompletion(exec::shutdownNow);
        emitter.onTimeout(exec::shutdownNow);
        return emitter;
    }
}
