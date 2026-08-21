package com.sinapse.ccm.modbus;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
@Service
public class ModbusService {

    // --- Injeção de Dependências ---
    private final TagCatalog ccm1Catalog;
    private final TagCatalog ccm2Catalog;

    // --- Configuração ---
    @Value("${modbus.host}") private String host;
    @Value("${modbus.port:502}") private int port;

    @Value("${modbus.poll-ms:1000}") private long pollMs;

    @Value("${modbus.simulation.enabled:false}")
    private boolean simulationEnabled;

    // --- Constantes Modbus ---
    private static final int COIL_BASE = 1;
    private static final int DI_BASE   = 1001;
    private static final int IR_BASE   = 3001;
    private static final int HR_BASE   = 4001;

    public ModbusService(@Qualifier("ccm1Catalog") TagCatalog ccm1Catalog,
                         @Qualifier("ccm2Catalog") TagCatalog ccm2Catalog) {
        this.ccm1Catalog = ccm1Catalog;
        this.ccm2Catalog = ccm2Catalog;
    }

    public enum CcmKey {
        CCM1("ccm1"),
        CCM2("ccm2");
        private final String key;
        CcmKey(String key) { this.key = key; }
        public String getKey() { return key; }
        public static CcmKey fromString(String s) {
            if (s == null) return CCM1;
            String k = s.trim().toLowerCase();
            return switch (k) {
                case "ccm2" -> CCM2;
                case "ccm1" -> CCM1;
                default -> CCM1;
            };
        }
    }

    private static class CcmState {
        final TagCatalog catalog;
        final Map<String, TagValue> cache = new ConcurrentHashMap<>();

        CcmState(TagCatalog catalog) {
            this.catalog = catalog;
        }
    }

    private final Map<CcmKey, CcmState> states = new EnumMap<>(CcmKey.class);
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final Object connectionLock = new Object();
    private ModbusTCPMaster master;

    @PostConstruct
    void init() {
        states.put(CcmKey.CCM1, new CcmState(ccm1Catalog));
        states.put(CcmKey.CCM2, new CcmState(ccm2Catalog));

        if (simulationEnabled) {
            log.warn("=========================================");
            log.warn("MODO DE SIMULAÇÃO DE MODBUS ATIVADO");
            log.warn("Nenhuma conexão real com CLP será feita.");
            log.warn("=========================================");
        }

        Runnable task = () -> {
            for (CcmKey key : CcmKey.values()) {
                if (simulationEnabled) {
                    simulationPollCycle(key);
                } else {
                    pollCycle(key);
                }
            }
        };
        exec.scheduleAtFixedRate(task, 0, pollMs, TimeUnit.MILLISECONDS);
    }

    private void simulationPollCycle(CcmKey key) {
        CcmState state = stateOf(key);
        if (state.catalog == null) return;

        for (TagDef t : state.catalog.getTags()) {
            if (!t.isReadableOrDefault() || !t.isPolledOrDefault()) continue;

            double value = 0.0;
            String name = t.getName();

            if (name.endsWith("_S")) {
                // Simula status DI (0 ou 1) apenas para compatibilidade
                value = (System.currentTimeMillis() / 10000) % 2 == 0 ? 1.0 : 0.0;
            } else if (name.endsWith("_A")) {
                // Corrente
                String statusTag = name.replace("_A", "_S");
                boolean isActive = Optional.ofNullable(state.cache.get(statusTag))
                                           .map(v -> v.getValue() > 0)
                                           .orElse(false);
                if (isActive) {
                    value = random.nextDouble(5.0, 30.0);
                } else {
                    value = 0.0;
                }
            } else if (name.endsWith("_H")) {
                // Horímetro
                double currentVal = Optional.ofNullable(state.cache.get(name)).map(TagValue::getValue).orElse(0.0);
                value = currentVal + (pollMs / 3600000.0);
            } else if (name.endsWith("_F")) {
                // Simula Estado do Motor (HR): 0=Parado, 1=Partindo, 2=Funcionando, 3=Falha
                // Probabilidades: 40% Parado, 10% Partindo, 40% Funcionando, 10% Falha
                int rand = random.nextInt(100);
                if (rand < 40) value = 0.0;      // Parado
                else if (rand < 50) value = 1.0; // Partindo
                else if (rand < 90) value = 2.0; // Funcionando
                else value = 3.0;                // Falha
            } else if (name.equals("KW")) {
                value = random.nextDouble(80.0, 250.0);
            } else if (name.startsWith("FP")) {
                value = random.nextDouble(-1.0, 1.0);
            } else {
                value = random.nextDouble(0, 100);
            }

            state.cache.put(t.getName(), new TagValue(t.getName(), value, Instant.now(), TagValue.Quality.GOOD, "Simulated"));
        }
    }

    private void pollCycle(CcmKey key) {
        CcmState state = stateOf(key);
        if (state.catalog == null) return;

        synchronized (connectionLock) {
            try {
                if (!ensureConnected()) return;

                for (TagDef t : state.catalog.getTags()) {
                    if (!t.isReadableOrDefault() || !t.isPolledOrDefault()) continue;

                    try {
                        double value = switch (t.getType()) {
                            case HR   -> readHR(key, t);
                            case IR   -> readIR(key, t);
                            case COIL -> readCoil(key, t) ? 1.0 : 0.0;
                            case DI   -> readDI(key, t) ? 1.0 : 0.0;
                        };
                        state.cache.put(t.getName(), new TagValue(t.getName(), value, Instant.now(), TagValue.Quality.GOOD, null));
                    } catch (Exception ex) {
                        log.debug("Falha lendo {} [{}]: {}", t.getName(), key.getKey(), ex.getMessage());
                        state.cache.put(t.getName(), new TagValue(t.getName(), 0.0, Instant.now(), TagValue.Quality.BAD, ex.getMessage()));
                    }
                }
            } catch (Exception e) {
                log.warn("Erro no polling {}: {}", key.getKey(), e.getMessage());
            }
        }
    }

    public void write(String ccmKey, String name, int raw) throws Exception {
        if (simulationEnabled) {
            log.info("[SIMULAÇÃO] Escrita na tag '{}' do CCM '{}' com valor '{}'. Nenhuma ação real foi tomada.", name, ccmKey, raw);
            CcmKey key = CcmKey.fromString(ccmKey);
            CcmState state = stateOf(key);
            state.cache.put(name, new TagValue(name, raw, Instant.now(), TagValue.Quality.GOOD, "Simulated Write"));
            return;
        }

        CcmKey key = CcmKey.fromString(ccmKey);
        CcmState state = stateOf(key);

        TagDef t = state.catalog.byName(name);
        if (t == null) throw new IllegalArgumentException("Tag não encontrada '" + name + "' no CCM '" + ccmKey + "'");
        if (!t.isWritableOrDefault()) throw new IllegalArgumentException("Tag somente leitura: " + name);

        synchronized (connectionLock) {
            if (!ensureConnected()) throw new IllegalStateException("Sem conexão Modbus (" + key.getKey() + ")");

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
    }

    private void connect() {
        try {
            if (master != null) master.disconnect();
        } catch (Exception ignored) {}

        master = new ModbusTCPMaster(host, port);
        master.setTimeout(1000);
        try {
            master.connect();
            log.info("Conectado ao CLP compartilhado ({}:{})", host, port);
        } catch (Exception e) {
            log.warn("Falha ao conectar CLP compartilhado ({}:{}): {}", host, port, e.getMessage());
        }
    }

    private boolean ensureConnected() {
        if (simulationEnabled) {
            return true;
        }
        try {
            if (master == null || !master.isConnected()) {
                connect();
            }
        } catch (Exception e) {
            log.warn("Falha ao garantir conexão com o CLP compartilhado: {}", e.getMessage());
        }
        return master != null && master.isConnected();
    }

    private CcmState stateOf(CcmKey key) {
        CcmState s = states.get(key);
        if (s == null) throw new IllegalStateException("CCM não configurado: " + key);
        return s;
    }

    private static int swap16(int v) { return ((v & 0xFF) << 8) | ((v >>> 8) & 0xFF); }

    private static long buildU32(int hi, int lo, String endian) {
        String e = (endian == null ? "BIG" : endian).toUpperCase();
        hi &= 0xFFFF;
        lo &= 0xFFFF;
        return switch (e) {
            case "LITTLE", "WORD_SWAP" -> ((long) lo << 16) | hi;
            case "BYTE_SWAP" -> ((long) swap16(hi) << 16) | swap16(lo);
            case "BYTE_WORD_SWAP", "SWAP_BOTH" -> ((long) swap16(lo) << 16) | swap16(hi);
            default -> ((long) hi << 16) | lo;
        };
    }

    private double readHR(CcmKey key, TagDef t) throws Exception {
        ensureType(t, TagType.HR);
        if (!ensureConnected()) throw new IllegalStateException("Sem conexão Modbus (" + key.getKey() + ")");
        int ref = t.getAddress() - HR_BASE;
        int words = t.getWordsOrDefault();
        double scale = t.getScaleOrDefault();

        if (words <= 1) {
            Register[] r = master.readMultipleRegisters(t.getUnitIdOrDefault(), ref, 1);
            return (r[0].getValue() & 0xFFFF) * scale;
        }

        Register[] r = master.readMultipleRegisters(t.getUnitIdOrDefault(), ref, 2);
        long u32 = buildU32(r[0].getValue(), r[1].getValue(), t.getEndianOrDefault());

        return switch (t.getFormatOrDefault().toUpperCase()) {
            case "U32" -> (double) (u32 & 0xFFFFFFFFL) * scale;
            case "S32" -> (double) ((int) u32) * scale;
            case "F32" -> Float.intBitsToFloat((int) u32) * scale;
            default -> (r[0].getValue() & 0xFFFF) * scale;
        };
    }

    private double readIR(CcmKey key, TagDef t) throws Exception {
        ensureType(t, TagType.IR);
        if (!ensureConnected()) throw new IllegalStateException("Sem conexão Modbus (" + key.getKey() + ")");
        int ref = t.getAddress() - IR_BASE;
        InputRegister[] r = master.readInputRegisters(t.getUnitIdOrDefault(), ref, 1);
        return r[0].getValue() * t.getScaleOrDefault();
    }

    private boolean readCoil(CcmKey key, TagDef t) throws Exception {
        ensureType(t, TagType.COIL);
        if (!ensureConnected()) throw new IllegalStateException("Sem conexão Modbus (" + key.getKey() + ")");
        int ref = t.getAddress() - COIL_BASE;
        return master.readCoils(t.getUnitIdOrDefault(), ref, 1).getBit(0);
    }

    private boolean readDI(CcmKey key, TagDef t) throws Exception {
        ensureType(t, TagType.DI);
        if (!ensureConnected()) throw new IllegalStateException("Sem conexão Modbus (" + key.getKey() + ")");
        int ref = t.getAddress() - DI_BASE;
        return master.readInputDiscretes(t.getUnitIdOrDefault(), ref, 1).getBit(0);
    }

    public void write(String name, int raw) throws Exception { write("ccm1", name, raw); }
    public void pulse(String ccmKey, String name, int ms) throws Exception {
        if (ms <= 0) ms = 200;
        write(ccmKey, name, 1);
        try { Thread.sleep(ms); } finally { write(ccmKey, name, 0); }
    }
    public void pulse(String name, int ms) throws Exception { pulse("ccm1", name, ms); }
    public void latchEmergency(String ccmKey) throws Exception { write(ccmKey, "EMERGENCIA", 1); log.info("EMERGENCIA setada (retentivo) em {}.", ccmKey); }
    public void latchEmergency() throws Exception { latchEmergency("ccm1"); }
    public void clearEmergency(String ccmKey) throws Exception { write(ccmKey, "EMERGENCIA", 0); log.info("EMERGENCIA limpa manualmente em {}.", ccmKey); }
    public void clearEmergency() throws Exception { clearEmergency("ccm1"); }
    public void resetPulse(String ccmKey, int ms) throws Exception { pulse(ccmKey, "RESET", ms); log.info("RESET pulsado em {} ({} ms).", ccmKey, ms); }
    public void resetPulse(int ms) throws Exception { resetPulse("ccm1", ms); }

    public Map<String, TagValue> snapshot(String ccmKey) {
        CcmKey key = CcmKey.fromString(ccmKey);
        CcmState state = stateOf(key);
        return Map.copyOf(state.cache);
    }
    public Map<String, TagValue> snapshot() { return snapshot("ccm1"); }
    public Optional<TagValue> get(String ccmKey, String name) {
        CcmKey key = CcmKey.fromString(ccmKey);
        CcmState state = stateOf(key);
        return Optional.ofNullable(state.cache.get(name));
    }
    public Optional<TagValue> get(String name) { return get("ccm1", name); }

    private void ensureType(TagDef t, TagType expected) {
        if (t.getType() != expected) {
            throw new IllegalArgumentException("Tag '" + t.getName() + "' não é do tipo " + expected + " (é " + t.getType() + ")");
        }
    }

    @PreDestroy
    void shutdown() {
        exec.shutdownNow();
        if (simulationEnabled) return;
        synchronized (connectionLock) {
            try {
                if (master != null) master.disconnect();
            } catch (Exception ignored) {}
        }
    }
}
