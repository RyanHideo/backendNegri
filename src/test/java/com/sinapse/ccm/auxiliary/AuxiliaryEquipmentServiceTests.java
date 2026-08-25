package com.sinapse.ccm.auxiliary;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import com.sinapse.ccm.truckflow.TrafficLightService;
import com.sinapse.ccm.truckflow.TrafficLightSnapshot;
import com.sinapse.ccm.truckflow.TrafficLightStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuxiliaryEquipmentServiceTests {

    @Test
    void returnsUnavailablePlaceholdersWhenNoTagsAreConfigured() {
        ModbusService modbusService = mock(ModbusService.class);
        TrafficLightService trafficLightService = mock(TrafficLightService.class);
        when(trafficLightService.getCurrentSnapshot())
                .thenReturn(TrafficLightSnapshot.unknown(0));

        AuxiliaryEquipmentSnapshot snapshot = new AuxiliaryEquipmentService(
                modbusService,
                trafficLightService,
                false,
                ""
        ).snapshot();

        assertThat(snapshot.britadorPrimario().potenciaPercentual()).isNull();
        assertThat(snapshot.britadorPrimario().status()).isEqualTo("unavailable");
        assertThat(snapshot.semaforo().estado()).isEqualTo("desligado");
        assertThat(snapshot.semaforo().status()).isEqualTo("unavailable");
        assertThat(snapshot.contadorCaminhoes().contagem()).isNull();
        assertThat(snapshot.contadorCaminhoes().status()).isEqualTo("unavailable");
    }

    @Test
    void publishesConfiguredReadingsWithoutDependingOnModbusAddresses() {
        Instant timestamp = Instant.parse("2026-08-24T12:30:00Z");
        ModbusService modbusService = mock(ModbusService.class);
        TrafficLightService trafficLightService = mock(TrafficLightService.class);
        when(modbusService.get("PRIMARY_CRUSHER_POWER_PERCENT"))
                .thenReturn(Optional.of(new TagValue(
                        "PRIMARY_CRUSHER_POWER_PERCENT",
                        72.345,
                        timestamp,
                        TagValue.Quality.GOOD,
                        null
                )));
        when(trafficLightService.getCurrentSnapshot())
                .thenReturn(new TrafficLightSnapshot(
                        TrafficLightStatus.GREEN,
                        18,
                        TagValue.Quality.GOOD,
                        timestamp
                ));

        AuxiliaryEquipmentSnapshot snapshot = new AuxiliaryEquipmentService(
                modbusService,
                trafficLightService,
                true,
                "PRIMARY_CRUSHER_POWER_PERCENT"
        ).snapshot();

        assertThat(snapshot.britadorPrimario().potenciaPercentual()).isEqualTo(72.35);
        assertThat(snapshot.britadorPrimario().status()).isEqualTo("good");
        assertThat(snapshot.semaforo().estado()).isEqualTo("verde");
        assertThat(snapshot.semaforo().status()).isEqualTo("good");
        assertThat(snapshot.contadorCaminhoes().contagem()).isEqualTo(18);
        assertThat(snapshot.contadorCaminhoes().ultimoPulso()).isEqualTo(timestamp);
    }
}
