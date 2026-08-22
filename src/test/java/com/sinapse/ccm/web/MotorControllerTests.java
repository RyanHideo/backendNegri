package com.sinapse.ccm.web;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import com.sinapse.ccm.motors.MotorCatalog;
import com.sinapse.ccm.motors.MotorDef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MotorControllerTests {

    @Test
    void returnsMotorMetadataFromCatalogAndOnlyGoodReadings() {
        MotorCatalog catalog = mock(MotorCatalog.class);
        ModbusService modbus = mock(ModbusService.class);
        MotorDef motor = motor();

        when(catalog.getMotors()).thenReturn(List.of(motor));
        when(modbus.get("ccm1", "M7_S")).thenReturn(Optional.of(tag("M7_S", 1, TagValue.Quality.GOOD)));
        when(modbus.get("ccm1", "M7_A")).thenReturn(Optional.of(tag("M7_A", 8.5, TagValue.Quality.GOOD)));
        when(modbus.get("ccm1", "M7_F")).thenReturn(Optional.of(tag("M7_F", 0, TagValue.Quality.BAD)));
        when(modbus.get("ccm1", "M7_H")).thenReturn(Optional.empty());

        MotorController.MotorOverviewDto result = new MotorController(catalog, modbus)
                .getMotorsOverview()
                .getFirst();

        assertThat(result.getId()).isEqualTo("M7");
        assertThat(result.getName()).isEqualTo("BRITADOR HP300");
        assertThat(result.getCcm()).isEqualTo("ccm1");
        assertThat(result.getCategory()).isEqualTo("britador");
        assertThat(result.getNominalCurrent()).isEqualTo(10.0);
        assertThat(result.isHasInverter()).isFalse();
        assertThat(result.getStatusTagName()).isEqualTo("M7_S");
        assertThat(result.getCurrentTagName()).isEqualTo("M7_A");
        assertThat(result.getFaultTagName()).isEqualTo("M7_F");
        assertThat(result.getHoursTagName()).isEqualTo("M7_H");
        assertThat(result.getStatus()).isEqualTo(1.0);
        assertThat(result.getCurrent()).isEqualTo(8.5);
        assertThat(result.getFault()).isNull();
        assertThat(result.getHours()).isNull();
    }

    private static MotorDef motor() {
        MotorDef motor = new MotorDef();
        motor.setName("BRITADOR HP300");
        motor.setCcm("ccm1");
        motor.setCategory("britador");
        motor.setNominalCurrent(10.0);
        motor.setStatusTagName("M7_S");
        motor.setCurrentTagName("M7_A");
        motor.setFaultTagName("M7_F");
        motor.setHoursTagName("M7_H");
        motor.setHasInverter(false);
        return motor;
    }

    private static TagValue tag(String name, double value, TagValue.Quality quality) {
        return new TagValue(name, value, Instant.now(), quality, null);
    }
}
