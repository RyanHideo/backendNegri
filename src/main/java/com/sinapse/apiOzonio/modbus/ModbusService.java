package com.sinapse.apiOzonio.modbus;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    // Bases lógicas combinadas (endereçamento lógico que você convencionou)
    private static final int COIL_BASE = 1;    // 0xxxx -> 1-based (1,2,3…)
    private static final int DI_BASE   = 1001; // 1xxxx -> 1001…
    private static final int IR_BASE   = 3001; // 3xxxx -> 3001…
    private static final int HR_BASE   = 4001; // 4xxxx -> 4001…

    private ModbusTCPMaster master;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, TagValue> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        connect();
        exec.scheduleAtFixedRate(this::pollCycle, 0, pollMs, TimeUnit.MILLISECONDS);
    }

    private void connect() {
        try { if (master != null) master.disconnect(); } catch (Exception ignored) {}
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
                // Ignora tags não legíveis ou que não devem ser "polled"
                if (!t.isReadableOrDefault() || !t.isPolledOrDefault()) continue;

                try {
                    double value = switch (t.getType()) {
                        case HR   -> readHR(t);
                        case IR   -> readIR(t);
                        case COIL -> readCoil(t) ? 1.0 : 0.0;
                        case DI   -> readDI(t) ? 1.0 : 0.0;
                    };

                    cache.put(
                            t.getName(),
                            new TagValue(
                                    t.getName(),
                                    value,
                                    Instant.now(),
                                    TagValue.Quality.GOOD,
                                    null
                            )
                    );
                } catch (Exception ex) {
                    log.debug("Falha lendo {}: {}", t.getName(), ex.getMessage());
                    cache.put(
                            t.getName(),
                            new TagValue(
                                    t.getName(),
                                    0.0,
                                    Instant.now(),
                                    TagValue.Quality.BAD,
                                    ex.getMessage()
                            )
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Erro no polling: {}", e.getMessage());
        }
    }

    /* =================== LEITURAS =================== */

    private static int swap16(int v) {
        return ((v & 0xFF) << 8) | ((v >>> 8) & 0xFF);
    }

    /** Constrói U32 a partir de hi/lo conforme 'endian' */
    private static long buildU32(int hi, int lo, String endian) {
        String e = (endian == null ? "BIG" : endian).toUpperCase();
        hi &= 0xFFFF;
        lo &= 0xFFFF;

        return switch (e) {
            case "LITTLE", "WORD_SWAP" -> ((long) lo << 16) | hi;                         // lo-hi
            case "BYTE_SWAP" -> ((long) swap16(hi) << 16) | swap16(lo);                   // AB CD -> BA DC
            case "BYTE_WORD_SWAP", "SWAP_BOTH" -> ((long) swap16(lo) << 16) | swap16(hi); // AB CD -> DC BA
            case "BIG", "BE", "MSW" -> ((long) hi << 16) | lo;                            // hi-lo (padrão)
            default -> ((long) hi << 16) | lo;
        };
    }

    private double readHR(TagDef t) throws Exception {
        ensureType(t, TagType.HR);
        int ref = t.getAddress() - HR_BASE;

        int words      = t.getWordsOrDefault();         // 1 ou 2
        String fmt     = t.getFormatOrDefault();        // U16/U32/S32/F32
        String endian  = t.getEndianOrDefault();        // BIG/LITTLE
        double scale   = t.getScaleOrDefault();         // ex.: 0.01 p/ 2 casas

        if (words <= 1) {
            Register[] r = master.readMultipleRegisters(t.getUnitIdOrDefault(), ref, 1);
            int u16 = r[0].getValue() & 0xFFFF;
            return u16 * scale;
        }

        // words >= 2 -> ler 2 words
        Register[] r = master.readMultipleRegisters(t.getUnitIdOrDefault(), ref, 2);
        int hi = r[0].getValue();
        int lo = r[1].getValue();

        long u32 = buildU32(hi, lo, endian);

        switch (fmt.toUpperCase()) {
            case "U32":
                return (double) (u32 & 0xFFFFFFFFL) * scale;
            case "S32": {
                int s32 = (int) (u32 & 0xFFFFFFFFL);
                return (double) s32 * scale;
            }
            case "F32": {
                int bits = (int) (u32 & 0xFFFFFFFFL);
                float f = Float.intBitsToFloat(bits);
                return f * scale;
            }
            default:
                // fallback: trata como U16 do primeiro word
                int u16 = r[0].getValue() & 0xFFFF;
                return u16 * scale;
        }
    }

    private double readIR(TagDef t) throws Exception {
        ensureType(t, TagType.IR);
        int ref = t.getAddress() - IR_BASE;
        InputRegister[] r = master.readInputRegisters(t.getUnitIdOrDefault(), ref, 1);
        return r[0].getValue() * t.getScaleOrDefault();
    }

    private boolean readCoil(TagDef t) throws Exception {
        ensureType(t, TagType.COIL);
        int ref = t.getAddress() - COIL_BASE;
        return master.readCoils(t.getUnitIdOrDefault(), ref, 1).getBit(0);
    }

    private boolean readDI(TagDef t) throws Exception {
        ensureType(t, TagType.DI);
        int ref = t.getAddress() - DI_BASE;
        return master.readInputDiscretes(t.getUnitIdOrDefault(), ref, 1).getBit(0);
    }

    /* =================== ESCRITAS =================== */

    public void write(String name, int raw) throws Exception {
        TagDef t = catalog.byName(name);
        if (t == null) throw new IllegalArgumentException("Tag não encontrada: " + name);
        if (!t.isWritableOrDefault()) throw new IllegalArgumentException("Tag somente leitura: " + name);

        switch (t.getType()) {
            case HR -> {
                int ref = t.getAddress() - HR_BASE;
                master.writeSingleRegister(t.getUnitIdOrDefault(), ref, new SimpleRegister(raw));
            }
            case COIL -> {
                int ref = t.getAddress() - COIL_BASE;
                master.writeCoil(t.getUnitIdOrDefault(), ref, raw != 0);
            }
            default -> throw new IllegalArgumentException("Escrita suportada só para HR/COIL");
        }
    }

    /** Pulso não-retentivo (ex.: RESET) */
    public void pulse(String name, int ms) throws Exception {
        write(name, 1);
        try { Thread.sleep(ms); }
        finally { write(name, 0); }
    }

    /* =================== SNAPSHOT =================== */

    public Map<String, TagValue> snapshot() { return Map.copyOf(cache); }
    public Optional<TagValue> get(String name) { return Optional.ofNullable(cache.get(name)); }

    /* =================== UTILS =================== */

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
