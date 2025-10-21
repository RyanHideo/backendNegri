package com.sinapse.apiOzonio.modbus;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModbusService {

    private final TagCatalog catalog;

    @Value("${modbus.host}") private String host;
    @Value("${modbus.port}") private int port;
    @Value("${modbus.poll-ms:1000}") private long pollMs;

    // Bases lógicas (o que você pediu)
    private static final int COIL_BASE = 1;       // 0xxxx -> 1-based (1,2,3…)
    private static final int DI_BASE   = 1001;    // 1xxxx -> 1001…
    private static final int IR_BASE   = 3001;    // 3xxxx -> 3001…
    private static final int HR_BASE   = 4001;    // 4xxxx -> 4001…

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
        } catch (Exception ignored) { }
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

    /** ---------- LEITURAS ---------- */

    private double readHR(TagDef t) throws Exception {
        ensureType(t, TagType.HR);
        int ref = t.getAddress() - HR_BASE; // 4001 -> 0
        Register[] r = master.readMultipleRegisters(t.getUnitId(), ref, 1);
        return r[0].getValue() * t.getScale();
    }

    private double readIR(TagDef t) throws Exception {
        ensureType(t, TagType.IR);
        int ref = t.getAddress() - IR_BASE; // 3001 -> 0
        InputRegister[] r = master.readInputRegisters(t.getUnitId(), ref, 1);
        return r[0].getValue() * t.getScale();
    }

    private boolean readCoil(TagDef t) throws Exception {
        ensureType(t, TagType.COIL);
        int ref = t.getAddress() - COIL_BASE; // 1 -> 0
        return master.readCoils(t.getUnitId(), ref, 1).getBit(0);
    }

    private boolean readDI(TagDef t) throws Exception {
        ensureType(t, TagType.DI);
        int ref = t.getAddress() - DI_BASE; // 1001 -> 0
        return master.readInputDiscretes(t.getUnitId(), ref, 1).getBit(0);
    }

    /** ---------- ESCRITAS (o que o seu controller usa) ---------- */

    /** write(name, raw): usado pelo POST /api/tags/{name} */
    public void write(String name, int raw) throws Exception {
        TagDef t = catalog.byName(name);
        if (t == null) throw new IllegalArgumentException("Tag não encontrada: " + name);
        if (!t.isWritable()) throw new IllegalArgumentException("Tag somente leitura: " + name);

        switch (t.getType()) {
            case HR -> {
                int ref = t.getAddress() - HR_BASE; // 4001 -> 0
                master.writeSingleRegister(t.getUnitId(), ref, new SimpleRegister(raw));
            }
            case COIL -> {
                int ref = t.getAddress() - COIL_BASE; // 1 -> 0
                master.writeCoil(t.getUnitId(), ref, raw != 0);
            }
            default -> throw new IllegalArgumentException("Escrita suportada só para HR/COIL");
        }
    }

    /** pulse(name, ms): usado pelo POST /api/cmd/reset */
    public void pulse(String name, int ms) throws Exception {
        // liga
        write(name, 1);
        try {
            Thread.sleep(ms);
        } finally {
            // desliga (não-retentivo)
            write(name, 0);
        }
    }

    /** ---------- SNAPSHOT ---------- */

    public Map<String, TagValue> snapshot() { return Map.copyOf(cache); }

    public Optional<TagValue> get(String name) { return Optional.ofNullable(cache.get(name)); }

    /** ---------- UTILS ---------- */

    private void ensureType(TagDef t, TagType expected) {
        if (t.getType() != expected) {
            throw new IllegalArgumentException(
                    "Tag '" + t.getName() + "' não é do tipo " + expected + " (é " + t.getType() + ")"
            );
        }
    }

    @PreDestroy
    void shutdown() {
        exec.shutdownNow();
        try { if (master != null) master.disconnect(); } catch (Exception ignored) {}
    }
}
