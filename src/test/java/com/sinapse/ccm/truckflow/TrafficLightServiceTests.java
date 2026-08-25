package com.sinapse.ccm.truckflow;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrafficLightServiceTests {

    private ModbusService modbusService;
    private TruckCountStore truckCountStore;
    private Path stateFile;

    @BeforeEach
    void setUp() {
        modbusService = mock(ModbusService.class);
        stateFile = Path.of("target", "test-state", "truck_count-" + UUID.randomUUID() + ".properties");
        truckCountStore = new TruckCountStore(stateFile.toString());
        truckCountStore.initialize();
    }

    @Test
    void doesNotCountWhenTheFirstKnownStateIsGreen() {
        TrafficLightService service = trafficLightService();
        when(modbusService.get("TRAFFIC_LIGHT_POWER"))
                .thenReturn(Optional.of(tag("TRAFFIC_LIGHT_POWER", 1)));
        when(modbusService.get("TRAFFIC_LIGHT_COLOR"))
                .thenReturn(Optional.of(tag("TRAFFIC_LIGHT_COLOR", 1)));

        service.refresh();

        assertThat(service.getCurrentSnapshot().status()).isEqualTo(TrafficLightStatus.GREEN);
        assertThat(service.getCurrentSnapshot().truckCount()).isZero();
    }

    @Test
    void countsAndPersistsOnlyTheRedToGreenTransition() {
        TrafficLightService service = trafficLightService();
        when(modbusService.get("TRAFFIC_LIGHT_POWER"))
                .thenReturn(
                        Optional.of(tag("TRAFFIC_LIGHT_POWER", 1)),
                        Optional.of(tag("TRAFFIC_LIGHT_POWER", 1)),
                        Optional.of(tag("TRAFFIC_LIGHT_POWER", 1))
                );
        when(modbusService.get("TRAFFIC_LIGHT_COLOR"))
                .thenReturn(
                        Optional.of(tag("TRAFFIC_LIGHT_COLOR", 0)),
                        Optional.of(tag("TRAFFIC_LIGHT_COLOR", 1)),
                        Optional.of(tag("TRAFFIC_LIGHT_COLOR", 1))
                );

        service.refresh();
        service.refresh();
        service.refresh();

        assertThat(service.getCurrentSnapshot().truckCount()).isEqualTo(1);

        TruckCountStore reloadedStore = new TruckCountStore(stateFile.toString());
        reloadedStore.initialize();
        assertThat(reloadedStore.getCount()).isEqualTo(1);
    }

    @Test
    void returnsOffWhenThePowerBitIsZero() {
        TrafficLightService service = trafficLightService();
        when(modbusService.get("TRAFFIC_LIGHT_POWER"))
                .thenReturn(Optional.of(tag("TRAFFIC_LIGHT_POWER", 0)));

        service.refresh();

        assertThat(service.getCurrentSnapshot().status()).isEqualTo(TrafficLightStatus.OFF);
        assertThat(service.getCurrentSnapshot().truckCount()).isZero();
    }

    @Test
    void doesNotCountRedToOffToGreenAsADirectTransition() {
        TrafficLightService service = trafficLightService();
        when(modbusService.get("TRAFFIC_LIGHT_POWER"))
                .thenReturn(
                        Optional.of(tag("TRAFFIC_LIGHT_POWER", 1)),
                        Optional.of(tag("TRAFFIC_LIGHT_POWER", 0)),
                        Optional.of(tag("TRAFFIC_LIGHT_POWER", 1))
                );
        when(modbusService.get("TRAFFIC_LIGHT_COLOR"))
                .thenReturn(
                        Optional.of(tag("TRAFFIC_LIGHT_COLOR", 0)),
                        Optional.of(tag("TRAFFIC_LIGHT_COLOR", 1))
                );

        service.refresh();
        service.refresh();
        service.refresh();

        assertThat(service.getCurrentSnapshot().status()).isEqualTo(TrafficLightStatus.GREEN);
        assertThat(service.getCurrentSnapshot().truckCount()).isZero();
    }

    private TrafficLightService trafficLightService() {
        return new TrafficLightService(
                modbusService,
                truckCountStore,
                true,
                "TRAFFIC_LIGHT_COLOR",
                "TRAFFIC_LIGHT_POWER",
                0,
                1,
                0,
                1
        );
    }

    private static TagValue tag(String name, double value) {
        return new TagValue(name, value, Instant.now(), TagValue.Quality.GOOD, null);
    }
}
