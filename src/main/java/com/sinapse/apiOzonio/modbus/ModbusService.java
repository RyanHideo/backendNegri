package com.sinapse.apiOzonio.modbus;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister; // <- necessário para escrita
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModbusService {
    private final TagCatalog catalog;

    @Value("${modbus.host}") private String host;
    @Value("${modbus.port}") private int port;
    @Value("${modbus.poll-ms:1000}") private long pollMs;

    private ModbusTCPMaster master;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, TagValue> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        connect();
        exec.scheduleAtFixedRate(this::pollCycle, 0, pollMs, TimeUnit.MILLISECONDS);
    }

    private void connect() {
        try {
            if (master != null) master.disconnect();
        } catch (Exception ignored) {}
        master = new ModbusTCPMaster(host, port);
        master.setTimeout(1000);
        try {
            master.connect();
            log.info("Conectado ao CLP {}:{}", host, port);
        } catch (Exception e) {
            log.warn("Falha ao conectar CLP: {}", e.getMessage());
        }
    }

    private void pollCycle() {
        try {
            if (master == null || !master.isConnected()) connect();
            if (master == null || !master.isConnected()) return;

            for (TagDef t : catalog.getTags()) {
                try {
                    double value = switch (t.getType()) {
                        case HR -> readHR(t);
                        case IR -> readIR(t);
                        case COIL -> readCoil(t) ? 1.0 : 0.0;
                        case DI -> readDI(t) ? 1.0 : 0.0;
                    };
                    cache.put(t.getName(), new TagValue(t.getName(), value, Instant.now()));
                } catch (Exception ex) {
                    log.debug("Falha lendo {}: {}", t.getName(), ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Erro no polling: {}", e.getMessage());
        }
    }

    private double readHR(TagDef t) throws Exception {
        Register[] r = master.readMultipleRegisters(t.getUnitId(), t.getAddress() - 40001, 1);
        return r[0].getValue() * t.getScale();
    }

    private double readIR(TagDef t) throws Exception {
        InputRegister[] r = master.readInputRegisters(t.getUnitId(), t.getAddress() - 30001, 1);
        return r[0].getValue() * t.getScale();
    }

    private boolean readCoil(TagDef t) throws Exception {
        return master.readCoils(t.getUnitId(), t.getAddress() - 1, 1).getBit(0);
    }

    private boolean readDI(TagDef t) throws Exception {
        return master.readInputDiscretes(t.getUnitId(), t.getAddress() - 1, 1).getBit(0);
    }

    public Map<String, TagValue> snapshot() { return Map.copyOf(cache); }

    public Optional<TagValue> get(String name) { return Optional.ofNullable(cache.get(name)); }

    public void write(String name, int raw) throws Exception {
        TagDef t = catalog.byName(name);
        if (t == null) throw new IllegalArgumentException("Tag não encontrada");
        if (!t.isWritable()) throw new IllegalArgumentException("Tag somente leitura");
        switch (t.getType()) {
            case HR -> master.writeSingleRegister(
                    t.getUnitId(),
                    t.getAddress() - 40001,
                    new SimpleRegister(raw) // <- aqui o ajuste
            );
            case COIL -> master.writeCoil(
                    t.getUnitId(),
                    t.getAddress() - 1,
                    raw != 0
            );
            default -> throw new IllegalArgumentException("Escrita suportada só para HR/COIL");
        }
    }

    @PreDestroy
    void shutdown() {
        exec.shutdownNow();
        try { if (master != null) master.disconnect(); } catch (Exception ignored) {}
    }
}
