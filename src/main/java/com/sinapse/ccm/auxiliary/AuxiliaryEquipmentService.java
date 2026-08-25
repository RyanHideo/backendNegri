package com.sinapse.ccm.auxiliary;

import com.sinapse.ccm.auxiliary.AuxiliaryEquipmentSnapshot.PrimaryCrusherSnapshot;
import com.sinapse.ccm.auxiliary.AuxiliaryEquipmentSnapshot.SemaphoreSnapshot;
import com.sinapse.ccm.auxiliary.AuxiliaryEquipmentSnapshot.TruckCounterSnapshot;
import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import com.sinapse.ccm.truckflow.TrafficLightService;
import com.sinapse.ccm.truckflow.TrafficLightSnapshot;
import com.sinapse.ccm.truckflow.TrafficLightStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuxiliaryEquipmentService {

    private static final String GOOD = "good";
    private static final String UNAVAILABLE = "unavailable";

    private final ModbusService modbusService;
    private final TrafficLightService trafficLightService;
    private final boolean primaryCrusherEnabled;
    private final String primaryCrusherPowerPercentTag;

    public AuxiliaryEquipmentService(
            ModbusService modbusService,
            TrafficLightService trafficLightService,
            @Value("${auxiliary.primary-crusher.enabled:false}") boolean primaryCrusherEnabled,
            @Value("${auxiliary.primary-crusher.power-percent-tag:}") String primaryCrusherPowerPercentTag
    ) {
        this.modbusService = modbusService;
        this.trafficLightService = trafficLightService;
        this.primaryCrusherEnabled = primaryCrusherEnabled;
        this.primaryCrusherPowerPercentTag = primaryCrusherPowerPercentTag;
    }

    public AuxiliaryEquipmentSnapshot snapshot() {
        PrimaryCrusherSnapshot primaryCrusher = primaryCrusherSnapshot();
        TrafficLightSnapshot trafficLight = trafficLightService.getCurrentSnapshot();
        boolean trafficAvailable = trafficLight.quality() == TagValue.Quality.GOOD
                && trafficLight.status() != TrafficLightStatus.UNKNOWN;

        SemaphoreSnapshot semaphore = new SemaphoreSnapshot(
                trafficAvailable ? mapTrafficLightStatus(trafficLight.status()) : "desligado",
                trafficAvailable ? GOOD : UNAVAILABLE,
                trafficAvailable ? trafficLight.updatedAt() : null
        );
        TruckCounterSnapshot truckCounter = new TruckCounterSnapshot(
                trafficAvailable ? trafficLight.truckCount() : null,
                null,
                trafficAvailable ? trafficLight.updatedAt() : null,
                trafficAvailable ? GOOD : UNAVAILABLE
        );

        return new AuxiliaryEquipmentSnapshot(primaryCrusher, semaphore, truckCounter);
    }

    private PrimaryCrusherSnapshot primaryCrusherSnapshot() {
        if (!primaryCrusherEnabled
                || primaryCrusherPowerPercentTag == null
                || primaryCrusherPowerPercentTag.isBlank()) {
            return new PrimaryCrusherSnapshot(null, UNAVAILABLE, null);
        }

        TagValue reading = modbusService.get(primaryCrusherPowerPercentTag).orElse(null);
        if (reading == null || reading.getQuality() != TagValue.Quality.GOOD) {
            return new PrimaryCrusherSnapshot(null, UNAVAILABLE, reading == null ? null : reading.getTs());
        }

        double percentage = reading.getValue();
        if (!Double.isFinite(percentage) || percentage < 0 || percentage > 100) {
            return new PrimaryCrusherSnapshot(null, UNAVAILABLE, reading.getTs());
        }

        return new PrimaryCrusherSnapshot(roundToTwo(percentage), GOOD, reading.getTs());
    }

    private static String mapTrafficLightStatus(TrafficLightStatus status) {
        return switch (status) {
            case GREEN -> "verde";
            case RED -> "vermelho";
            case OFF, UNKNOWN -> "desligado";
        };
    }

    private static double roundToTwo(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
