package com.sinapse.ccm.web;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import com.sinapse.ccm.motors.MotorCatalog;
import com.sinapse.ccm.motors.MotorDef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/motors")
@CrossOrigin(origins = "*")
public class MotorController {

    private final MotorCatalog motorCatalog;
    private final ModbusService modbusService;

    @GetMapping("/overview")
    public List<MotorOverviewDto> getMotorsOverview() {
        return motorCatalog.getMotors().stream()
                .map(this::buildDtoForMotor)
                .collect(Collectors.toList());
    }

    private MotorOverviewDto buildDtoForMotor(MotorDef motor) {
        // Helper para buscar o valor de uma tag, retornando 0.0 se não encontrada
        Double status = getValue(motor.getCcm(), motor.getStatusTagName()).orElse(0.0);
        Double current = getValue(motor.getCcm(), motor.getCurrentTagName()).orElse(0.0);
        Double fault = getValue(motor.getCcm(), motor.getFaultTagName()).orElse(0.0);
        Double hours = getValue(motor.getCcm(), motor.getHoursTagName()).orElse(0.0);

        return new MotorOverviewDto(
                motor.getName(),
                motor.getCcm(),
                status,
                current,
                fault,
                hours
        );
    }

    private Optional<Double> getValue(String ccm, String tagName) {
        if (tagName == null || tagName.isBlank()) {
            return Optional.empty();
        }
        return modbusService.get(ccm, tagName).map(TagValue::getValue);
    }

    /**
     * DTO para a resposta da API.
     */
    @Data
    @AllArgsConstructor
    public static class MotorOverviewDto {
        private String name;
        private String ccm;
        private Double status;
        private Double current;
        private Double fault;
        private Double hours;
    }
}
