package com.sinapse.ccm.truckflow;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@PropertySource("classpath:truckflow.properties")
public class TrafficLightService {

    private static final double EPSILON = 0.000001;

    private final ModbusService modbusService;
    private final TruckCountStore truckCountStore;
    private final boolean enabled;
    private final String ccm;
    private final String stateTag;
    private final String redTag;
    private final String greenTag;
    private final double redValue;
    private final double greenValue;

    private volatile TrafficLightSnapshot currentSnapshot;
    private TrafficLightStatus previousStatus = TrafficLightStatus.UNKNOWN;

    public TrafficLightService(
            ModbusService modbusService,
            TruckCountStore truckCountStore,
            @Value("${truckflow.enabled:false}") boolean enabled,
            @Value("${truckflow.ccm:ccm1}") String ccm,
            @Value("${truckflow.state-tag:}") String stateTag,
            @Value("${truckflow.red-tag:}") String redTag,
            @Value("${truckflow.green-tag:}") String greenTag,
            @Value("${truckflow.red-value:0}") double redValue,
            @Value("${truckflow.green-value:1}") double greenValue
    ) {
        this.modbusService = modbusService;
        this.truckCountStore = truckCountStore;
        this.enabled = enabled;
        this.ccm = ccm;
        this.stateTag = stateTag;
        this.redTag = redTag;
        this.greenTag = greenTag;
        this.redValue = redValue;
        this.greenValue = greenValue;
        this.currentSnapshot = TrafficLightSnapshot.unknown(truckCountStore.getCount());
    }

    @PostConstruct
    void logConfiguration() {
        if (!enabled) {
            log.info("Fluxo de caminhoes desativado. Configure as tags e use truckflow.enabled=true para ativar.");
            return;
        }

        if (usesSingleTag()) {
            log.info("Semaforo configurado com uma tag: {} em {} (vermelho={}, verde={}).",
                    stateTag, ccm, redValue, greenValue);
        } else if (usesTwoTags()) {
            log.info("Semaforo configurado com duas tags em {}: vermelho={}, verde={}.",
                    ccm, redTag, greenTag);
        } else {
            log.warn("Fluxo de caminhoes ativado, mas nenhuma configuracao valida de tags foi informada.");
        }
    }

    @Scheduled(fixedDelayString = "${truckflow.poll-ms:1000}")
    public synchronized void refresh() {
        if (!enabled) {
            return;
        }

        Observation observation = readObservation();
        long truckCount = truckCountStore.getCount();

        if (previousStatus == TrafficLightStatus.RED
                && observation.status() == TrafficLightStatus.GREEN
                && observation.quality() == TagValue.Quality.GOOD) {
            truckCount = truckCountStore.incrementAndGet();
            log.info("Transicao RED -> GREEN detectada. Total de caminhoes: {}.", truckCount);
        }

        previousStatus = observation.status();
        currentSnapshot = new TrafficLightSnapshot(
                observation.status(),
                truckCount,
                observation.quality(),
                observation.updatedAt()
        );
    }

    public TrafficLightSnapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    private Observation readObservation() {
        if (usesSingleTag()) {
            return readSingleTag();
        }
        if (usesTwoTags()) {
            return readTwoTags();
        }
        return Observation.unknown(TagValue.Quality.STALE);
    }

    private Observation readSingleTag() {
        TagValue tagValue = modbusService.get(ccm, stateTag).orElse(null);
        if (tagValue == null) {
            return Observation.unknown(TagValue.Quality.STALE);
        }
        if (tagValue.getQuality() != TagValue.Quality.GOOD) {
            return new Observation(TrafficLightStatus.UNKNOWN, tagValue.getQuality(), tagValue.getTs());
        }

        TrafficLightStatus status = TrafficLightStatus.UNKNOWN;
        if (equalsValue(tagValue.getValue(), redValue)) {
            status = TrafficLightStatus.RED;
        } else if (equalsValue(tagValue.getValue(), greenValue)) {
            status = TrafficLightStatus.GREEN;
        }

        return new Observation(status, tagValue.getQuality(), tagValue.getTs());
    }

    private Observation readTwoTags() {
        TagValue redTagValue = modbusService.get(ccm, redTag).orElse(null);
        TagValue greenTagValue = modbusService.get(ccm, greenTag).orElse(null);

        if (redTagValue == null || greenTagValue == null) {
            return Observation.unknown(TagValue.Quality.STALE);
        }

        TagValue.Quality quality = combineQuality(redTagValue.getQuality(), greenTagValue.getQuality());
        java.time.Instant updatedAt = latest(redTagValue.getTs(), greenTagValue.getTs());
        if (quality != TagValue.Quality.GOOD) {
            return new Observation(TrafficLightStatus.UNKNOWN, quality, updatedAt);
        }

        boolean redActive = isActive(redTagValue.getValue());
        boolean greenActive = isActive(greenTagValue.getValue());
        TrafficLightStatus status = TrafficLightStatus.UNKNOWN;

        if (redActive && !greenActive) {
            status = TrafficLightStatus.RED;
        } else if (greenActive && !redActive) {
            status = TrafficLightStatus.GREEN;
        }

        return new Observation(status, quality, updatedAt);
    }

    private boolean usesSingleTag() {
        return stateTag != null && !stateTag.isBlank();
    }

    private boolean usesTwoTags() {
        return redTag != null && !redTag.isBlank()
                && greenTag != null && !greenTag.isBlank();
    }

    private static boolean equalsValue(double actual, double expected) {
        return Math.abs(actual - expected) < EPSILON;
    }

    private static boolean isActive(double value) {
        return Math.abs(value) >= EPSILON;
    }

    private static TagValue.Quality combineQuality(TagValue.Quality first, TagValue.Quality second) {
        if (first == TagValue.Quality.BAD || second == TagValue.Quality.BAD) {
            return TagValue.Quality.BAD;
        }
        if (first == TagValue.Quality.STALE || second == TagValue.Quality.STALE) {
            return TagValue.Quality.STALE;
        }
        return TagValue.Quality.GOOD;
    }

    private static java.time.Instant latest(java.time.Instant first, java.time.Instant second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }

    private record Observation(
            TrafficLightStatus status,
            TagValue.Quality quality,
            java.time.Instant updatedAt
    ) {
        private static Observation unknown(TagValue.Quality quality) {
            return new Observation(TrafficLightStatus.UNKNOWN, quality, java.time.Instant.now());
        }
    }
}
