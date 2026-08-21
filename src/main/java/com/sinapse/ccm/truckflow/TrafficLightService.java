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
    private final String colorTag;
    private final String powerTag;
    private final double redValue;
    private final double greenValue;
    private final double powerOffValue;
    private final double powerOnValue;

    private volatile TrafficLightSnapshot currentSnapshot;
    private TrafficLightStatus previousStatus = TrafficLightStatus.UNKNOWN;

    public TrafficLightService(
            ModbusService modbusService,
            TruckCountStore truckCountStore,
            @Value("${truckflow.enabled:false}") boolean enabled,
            @Value("${truckflow.ccm:ccm1}") String ccm,
            @Value("${truckflow.color-tag:}") String colorTag,
            @Value("${truckflow.power-tag:}") String powerTag,
            @Value("${truckflow.red-value:0}") double redValue,
            @Value("${truckflow.green-value:1}") double greenValue,
            @Value("${truckflow.power-off-value:0}") double powerOffValue,
            @Value("${truckflow.power-on-value:1}") double powerOnValue
    ) {
        this.modbusService = modbusService;
        this.truckCountStore = truckCountStore;
        this.enabled = enabled;
        this.ccm = ccm;
        this.colorTag = colorTag;
        this.powerTag = powerTag;
        this.redValue = redValue;
        this.greenValue = greenValue;
        this.powerOffValue = powerOffValue;
        this.powerOnValue = powerOnValue;
        this.currentSnapshot = TrafficLightSnapshot.unknown(truckCountStore.getCount());
    }

    @PostConstruct
    void logConfiguration() {
        if (!enabled) {
            log.info("Fluxo de caminhoes desativado. Configure as tags e use truckflow.enabled=true para ativar.");
            return;
        }

        if (hasRequiredTags()) {
            log.info("Semaforo configurado em {}: tag de energia={}, tag de cor={}.",
                    ccm, powerTag, colorTag);
        } else {
            log.warn("Fluxo de caminhoes ativado, mas as tags de energia e cor nao foram informadas.");
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
        if (!hasRequiredTags()) {
            return Observation.unknown(TagValue.Quality.STALE);
        }

        TagValue powerTagValue = modbusService.get(ccm, powerTag).orElse(null);
        if (powerTagValue == null) {
            return Observation.unknown(TagValue.Quality.STALE);
        }
        if (powerTagValue.getQuality() != TagValue.Quality.GOOD) {
            return new Observation(
                    TrafficLightStatus.UNKNOWN,
                    powerTagValue.getQuality(),
                    powerTagValue.getTs()
            );
        }

        if (equalsValue(powerTagValue.getValue(), powerOffValue)) {
            return new Observation(
                    TrafficLightStatus.OFF,
                    powerTagValue.getQuality(),
                    powerTagValue.getTs()
            );
        }

        if (!equalsValue(powerTagValue.getValue(), powerOnValue)) {
            return new Observation(
                    TrafficLightStatus.UNKNOWN,
                    powerTagValue.getQuality(),
                    powerTagValue.getTs()
            );
        }

        TagValue colorTagValue = modbusService.get(ccm, colorTag).orElse(null);
        if (colorTagValue == null) {
            return Observation.unknown(TagValue.Quality.STALE);
        }

        TagValue.Quality quality = combineQuality(powerTagValue.getQuality(), colorTagValue.getQuality());
        java.time.Instant updatedAt = latest(powerTagValue.getTs(), colorTagValue.getTs());
        if (quality != TagValue.Quality.GOOD) {
            return new Observation(TrafficLightStatus.UNKNOWN, quality, updatedAt);
        }

        TrafficLightStatus status = TrafficLightStatus.UNKNOWN;
        if (equalsValue(colorTagValue.getValue(), redValue)) {
            status = TrafficLightStatus.RED;
        } else if (equalsValue(colorTagValue.getValue(), greenValue)) {
            status = TrafficLightStatus.GREEN;
        }

        return new Observation(status, quality, updatedAt);
    }

    private boolean hasRequiredTags() {
        return colorTag != null && !colorTag.isBlank()
                && powerTag != null && !powerTag.isBlank();
    }

    private static boolean equalsValue(double actual, double expected) {
        return Math.abs(actual - expected) < EPSILON;
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
