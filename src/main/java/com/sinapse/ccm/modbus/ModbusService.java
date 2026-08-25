package com.sinapse.ccm.modbus;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.sinapse.ccm.vsi.VsiPowerService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ModbusService {

    private static final int COIL_BASE = 1;
    private static final int DI_BASE = 1001;
    private static final int IR_BASE = 3001;
    private static final int HR_BASE = 4001;

    private final TagCatalog tagCatalog;
    private final VsiPowerService vsiPowerService;
    private final Map<String, TagValue> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final Object connectionLock = new Object();

    @Value("${modbus.host}")
    private String host;

    @Value("${modbus.port:502}")
    private int port;

    @Value("${modbus.poll-ms:1000}")
    private long pollMs;

    @Value("${modbus.simulation.enabled:false}")
    private boolean simulationEnabled;

    private ModbusTCPMaster master;

    public ModbusService(TagCatalog tagCatalog, VsiPowerService vsiPowerService) {
        this.tagCatalog = tagCatalog;
        this.vsiPowerService = vsiPowerService;
    }

    @PostConstruct
    void init() {
        if (simulationEnabled) {
            log.warn("=========================================");
            log.warn("MODO DE SIMULAÇÃO DE MODBUS ATIVADO");
            log.warn("Nenhuma conexão real com CLP será feita.");
            log.warn("=========================================");
        }

        Runnable task = simulationEnabled ? this::simulationPollCycle : this::pollCycle;
        exec.scheduleAtFixedRate(task, 0, pollMs, TimeUnit.MILLISECONDS);
    }

    private void simulationPollCycle() {
        for (TagDef tag : tagCatalog.getTags()) {
            if (!tag.isReadableOrDefault() || !tag.isPolledOrDefault()) continue;

            double value = simulatedValue(tag);
            cache.put(tag.getName(), new TagValue(
                    tag.getName(),
                    value,
                    Instant.now(),
                    TagValue.Quality.GOOD,
                    "Simulated"
            ));
        }
    }

    private double simulatedValue(TagDef tag) {
        String name = tag.getName();
        if (name.endsWith("_S")) {
            return (System.currentTimeMillis() / 10000) % 2 == 0 ? 1.0 : 0.0;
        }
        if (name.endsWith("_A")) {
            String statusTag = name.replace("_A", "_S");
            boolean active = Optional.ofNullable(cache.get(statusTag))
                    .map(value -> value.getValue() > 0)
                    .orElse(false);
            return active ? random.nextDouble(5.0, 30.0) : 0.0;
        }
        if (name.endsWith("_H")) {
            double currentValue = Optional.ofNullable(cache.get(name))
                    .map(TagValue::getValue)
                    .orElse(0.0);
            return currentValue + (pollMs / 3_600_000.0);
        }
        if (name.endsWith("_F")) {
            int result = random.nextInt(100);
            if (result < 40) return 0.0;
            if (result < 50) return 1.0;
            if (result < 90) return 2.0;
            return 3.0;
        }
        if (name.equals("KW")) return random.nextDouble(80.0, 250.0);
        if (name.startsWith("FP")) return random.nextDouble(-1.0, 1.0);
        return random.nextDouble(0, 100);
    }

    private void pollCycle() {
        synchronized (connectionLock) {
            try {
                if (!ensureConnected()) return;

                for (TagDef tag : tagCatalog.getTags()) {
                    if (!tag.isReadableOrDefault() || !tag.isPolledOrDefault()) continue;

                    try {
                        double value = switch (tag.getType()) {
                            case HR -> readHR(tag);
                            case IR -> readIR(tag);
                            case COIL -> readCoil(tag) ? 1.0 : 0.0;
                            case DI -> readDI(tag) ? 1.0 : 0.0;
                        };
                        cache.put(tag.getName(), new TagValue(
                                tag.getName(),
                                value,
                                Instant.now(),
                                TagValue.Quality.GOOD,
                                null
                        ));
                    } catch (Exception exception) {
                        log.debug("Falha lendo {}: {}", tag.getName(), exception.getMessage());
                        cache.put(tag.getName(), new TagValue(
                                tag.getName(),
                                0.0,
                                Instant.now(),
                                TagValue.Quality.BAD,
                                exception.getMessage()
                        ));
                    }
                }
            } catch (Exception exception) {
                log.warn("Erro no polling do CCM: {}", exception.getMessage());
            }
        }
    }

    public void write(String name, int raw) throws Exception {
        if (simulationEnabled) {
            log.info("[SIMULAÇÃO] Escrita na tag '{}' com valor '{}'. Nenhuma ação real foi tomada.", name, raw);
            cache.put(name, new TagValue(name, raw, Instant.now(), TagValue.Quality.GOOD, "Simulated Write"));
            return;
        }

        TagDef tag = tagCatalog.byName(name);
        if (tag == null) throw new IllegalArgumentException("Tag não encontrada: " + name);
        if (!tag.isWritableOrDefault()) throw new IllegalArgumentException("Tag somente leitura: " + name);

        synchronized (connectionLock) {
            if (!ensureConnected()) throw new IllegalStateException("Sem conexão Modbus");

            switch (tag.getType()) {
                case HR -> {
                    int reference = tag.getAddress() - HR_BASE;
                    master.writeSingleRegister(tag.getUnitIdOrDefault(), reference, new SimpleRegister(raw));
                }
                case COIL -> {
                    int reference = tag.getAddress() - COIL_BASE;
                    master.writeCoil(tag.getUnitIdOrDefault(), reference, raw != 0);
                }
                default -> throw new IllegalArgumentException("Escrita suportada só para HR/COIL");
            }
        }
    }

    public void pulse(String name, int milliseconds) throws Exception {
        int duration = milliseconds <= 0 ? 200 : milliseconds;
        write(name, 1);
        try {
            Thread.sleep(duration);
        } finally {
            write(name, 0);
        }
    }

    public void latchEmergency() throws Exception {
        write("EMERGENCIA", 1);
        log.info("EMERGENCIA setada (retentivo).");
    }

    public void clearEmergency() throws Exception {
        write("EMERGENCIA", 0);
        log.info("EMERGENCIA limpa manualmente.");
    }

    public void resetPulse(int milliseconds) throws Exception {
        pulse("RESET", milliseconds);
        log.info("RESET pulsado ({} ms).", milliseconds);
    }

    public Map<String, TagValue> snapshot() {
        Map<String, TagValue> tags = new HashMap<>(cache);
        injectCalculatedConsumption(tags);
        vsiPowerService.addKvaComparison(tags);
        return Map.copyOf(tags);
    }

    public Optional<TagValue> get(String name) {
        if ("CONSUMO".equalsIgnoreCase(name) || vsiPowerService.isCalculatedTag(name)) {
            return snapshot().entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .findFirst();
        }
        return Optional.ofNullable(cache.get(name));
    }

    private void connect() {
        try {
            if (master != null) master.disconnect();
        } catch (Exception ignored) {
        }

        master = new ModbusTCPMaster(host, port);
        master.setTimeout(1000);
        try {
            master.connect();
            log.info("Conectado ao CLP do CCM ({}:{})", host, port);
        } catch (Exception exception) {
            log.warn("Falha ao conectar ao CLP do CCM ({}:{}): {}", host, port, exception.getMessage());
        }
    }

    private boolean ensureConnected() {
        if (simulationEnabled) return true;
        try {
            if (master == null || !master.isConnected()) connect();
        } catch (Exception exception) {
            log.warn("Falha ao garantir conexão com o CLP: {}", exception.getMessage());
        }
        return master != null && master.isConnected();
    }

    private static int swap16(int value) {
        return ((value & 0xFF) << 8) | ((value >>> 8) & 0xFF);
    }

    private static long buildU32(int high, int low, String endian) {
        String normalizedEndian = endian == null ? "BIG" : endian.toUpperCase();
        high &= 0xFFFF;
        low &= 0xFFFF;
        return switch (normalizedEndian) {
            case "LITTLE", "WORD_SWAP" -> ((long) low << 16) | high;
            case "BYTE_SWAP" -> ((long) swap16(high) << 16) | swap16(low);
            case "BYTE_WORD_SWAP", "SWAP_BOTH" -> ((long) swap16(low) << 16) | swap16(high);
            default -> ((long) high << 16) | low;
        };
    }

    private double readHR(TagDef tag) throws Exception {
        ensureType(tag, TagType.HR);
        if (!ensureConnected()) throw new IllegalStateException("Sem conexão Modbus");
        int reference = tag.getAddress() - HR_BASE;
        int words = tag.getWordsOrDefault();
        double scale = tag.getScaleOrDefault();

        if (words <= 1) {
            Register[] registers = master.readMultipleRegisters(tag.getUnitIdOrDefault(), reference, 1);
            return (registers[0].getValue() & 0xFFFF) * scale;
        }

        Register[] registers = master.readMultipleRegisters(tag.getUnitIdOrDefault(), reference, 2);
        long unsignedValue = buildU32(registers[0].getValue(), registers[1].getValue(), tag.getEndianOrDefault());
        return switch (tag.getFormatOrDefault().toUpperCase()) {
            case "U32" -> (double) (unsignedValue & 0xFFFFFFFFL) * scale;
            case "S32" -> (double) ((int) unsignedValue) * scale;
            case "F32" -> Float.intBitsToFloat((int) unsignedValue) * scale;
            default -> (registers[0].getValue() & 0xFFFF) * scale;
        };
    }

    private double readIR(TagDef tag) throws Exception {
        ensureType(tag, TagType.IR);
        if (!ensureConnected()) throw new IllegalStateException("Sem conexão Modbus");
        int reference = tag.getAddress() - IR_BASE;
        InputRegister[] registers = master.readInputRegisters(tag.getUnitIdOrDefault(), reference, 1);
        return registers[0].getValue() * tag.getScaleOrDefault();
    }

    private boolean readCoil(TagDef tag) throws Exception {
        ensureType(tag, TagType.COIL);
        if (!ensureConnected()) throw new IllegalStateException("Sem conexão Modbus");
        int reference = tag.getAddress() - COIL_BASE;
        return master.readCoils(tag.getUnitIdOrDefault(), reference, 1).getBit(0);
    }

    private boolean readDI(TagDef tag) throws Exception {
        ensureType(tag, TagType.DI);
        if (!ensureConnected()) throw new IllegalStateException("Sem conexão Modbus");
        int reference = tag.getAddress() - DI_BASE;
        return master.readInputDiscretes(tag.getUnitIdOrDefault(), reference, 1).getBit(0);
    }

    private void injectCalculatedConsumption(Map<String, TagValue> tags) {
        TagValue consumption1 = Optional.ofNullable(tags.get("KWH1")).orElse(tags.get("CONSUMO1"));
        TagValue consumption2 = Optional.ofNullable(tags.get("KWH2")).orElse(tags.get("CONSUMO2"));
        if (consumption1 == null || consumption2 == null) return;

        try {
            double totalConsumption = (consumption1.getValue() * 65536.0) + consumption2.getValue();
            tags.put("CONSUMO", new TagValue(
                    "CONSUMO",
                    totalConsumption,
                    Instant.now(),
                    TagValue.Quality.GOOD,
                    null
            ));
        } catch (Exception exception) {
            log.warn("Falha ao calcular CONSUMO a partir de KWH1/KWH2 ou CONSUMO1/CONSUMO2: {}",
                    exception.getMessage());
        }
    }

    private void ensureType(TagDef tag, TagType expected) {
        if (tag.getType() != expected) {
            throw new IllegalArgumentException(
                    "Tag '" + tag.getName() + "' não é do tipo " + expected + " (é " + tag.getType() + ")"
            );
        }
    }

    @PreDestroy
    void shutdown() {
        exec.shutdownNow();
        if (simulationEnabled) return;
        synchronized (connectionLock) {
            try {
                if (master != null) master.disconnect();
            } catch (Exception ignored) {
            }
        }
    }
}
