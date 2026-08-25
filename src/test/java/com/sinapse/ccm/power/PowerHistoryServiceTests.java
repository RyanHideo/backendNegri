package com.sinapse.ccm.power;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PowerHistoryServiceTests {

    @Test
    void keepsOnlyTheTenMostRecentGoodSamplesForEachSeries() {
        ModbusService modbus = mock(ModbusService.class);
        AtomicInteger value = new AtomicInteger();
        when(modbus.snapshot()).thenAnswer(invocation -> {
            int current = value.getAndIncrement();
            return Map.of(
                    PowerHistoryService.MAIN_TRANSFORMER_TAG, tag("KW", current, TagValue.Quality.GOOD),
                    PowerHistoryService.VSI_TRANSFORMER_TAG, tag("PotenciaTrafoVsiKva", current + 100, TagValue.Quality.GOOD),
                    PowerHistoryService.GENERAL_TAG, tag("PotenciaGeralKva", current + 200, TagValue.Quality.GOOD)
            );
        });

        PowerHistoryService service = new PowerHistoryService(modbus);
        for (int index = 0; index < 12; index++) {
            service.capture();
        }

        PowerHistoryService.PowerHistorySnapshot history = service.snapshot();
        assertThat(history.mainTransformer()).hasSize(10);
        assertThat(history.mainTransformer().getFirst().value()).isEqualTo(2.0);
        assertThat(history.mainTransformer().getLast().value()).isEqualTo(11.0);
        assertThat(history.vsiTransformer()).hasSize(10);
        assertThat(history.general()).hasSize(10);
    }

    @Test
    void ignoresBadReadings() {
        ModbusService modbus = mock(ModbusService.class);
        when(modbus.snapshot()).thenReturn(Map.of(
                PowerHistoryService.MAIN_TRANSFORMER_TAG, tag("KW", 500, TagValue.Quality.BAD)
        ));

        PowerHistoryService service = new PowerHistoryService(modbus);
        service.capture();

        assertThat(service.snapshot().mainTransformer()).isEmpty();
    }

    private static TagValue tag(String name, double value, TagValue.Quality quality) {
        return new TagValue(name, value, Instant.now(), quality, null);
    }
}
