package com.sinapse.ccm.web;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import com.sinapse.ccm.motors.MotorCatalog;
import com.sinapse.ccm.motors.MotorDef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/efficiency")
@CrossOrigin(origins = "*")
public class EfficiencyController {

    private final EfficiencyService efficiencyService;

    @GetMapping("/productive")
    public ProductiveEfficiencySnapshot getProductiveEfficiency() {
        return efficiencyService.calculateProductiveEfficiency();
    }

    @GetMapping("/energy")
    public EnergyEfficiencySnapshot getEnergyEfficiency() {
        return efficiencyService.calculateEnergyEfficiency();
    }

    @GetMapping("/elevators/load")
    public List<IndividualLoadSnapshot> getIndividualElevatorLoads() {
        return efficiencyService.getIndividualElevatorLoads();
    }

    @Service
    @RequiredArgsConstructor
    public static class EfficiencyService {

        private final MotorCatalog motorCatalog;
        private final ModbusService modbusService;

        private static final Set<String> PRODUCTIVE_CATEGORIES = Set.of("elevador", "redler");
        private static final String ELEVATOR_CATEGORY = "elevador";

        public List<IndividualLoadSnapshot> getIndividualElevatorLoads() {
            List<IndividualLoadSnapshot> result = new ArrayList<>();

            // 1. Filtra apenas os motores que são elevadores
            List<MotorDef> elevators = motorCatalog.getMotors().stream()
                    .filter(m -> ELEVATOR_CATEGORY.equalsIgnoreCase(m.getCategory()))
                    .toList();

            for (MotorDef elevator : elevators) {
                boolean isActive = modbusService.get(elevator.getCcm(), elevator.getStatusTagName())
                        .map(tag -> tag.getValue() > 0).orElse(false);

                double actualCurrent = 0;
                double loadPercentage = 0;

                if (isActive) {
                    double valueFromPlc = modbusService.get(elevator.getCcm(), elevator.getCurrentTagName())
                            .map(TagValue::getValue).orElse(0.0);

                    actualCurrent = elevator.isHasInverter()
                            ? elevator.getNominalCurrent() - valueFromPlc
                            : valueFromPlc;
                    actualCurrent = Math.max(0, actualCurrent);

                    if (elevator.getNominalCurrent() > 0) {
                        loadPercentage = actualCurrent / elevator.getNominalCurrent();
                    }
                }

                result.add(new IndividualLoadSnapshot(
                        elevator.getName(),
                        elevator.getCcm(),
                        isActive ? "ACTIVE" : "INACTIVE",
                        loadPercentage,
                        actualCurrent,
                        elevator.getNominalCurrent()
                ));
            }
            return result;
        }

        public ProductiveEfficiencySnapshot calculateProductiveEfficiency() {
            // ... (lógica existente, sem alterações)
            double totalActiveCurrent = 0;
            double totalNominalCurrentOfActives = 0;
            int activeProductiveMotorsCount = 0;
            List<String> activeMotorNames = new java.util.ArrayList<>();

            List<MotorDef> productiveMotors = motorCatalog.getMotors().stream()
                    .filter(m -> PRODUCTIVE_CATEGORIES.contains(m.getCategory().toLowerCase()))
                    .toList();

            for (MotorDef motor : productiveMotors) {
                boolean isActive = modbusService.get(motor.getCcm(), motor.getStatusTagName())
                        .map(tag -> tag.getValue() > 0).orElse(false);

                if (isActive) {
                    double valueFromPlc = modbusService.get(motor.getCcm(), motor.getCurrentTagName())
                            .map(TagValue::getValue).orElse(0.0);
                    
                    double actualCurrent = motor.isHasInverter()
                        ? motor.getNominalCurrent() - valueFromPlc
                        : valueFromPlc;
                    actualCurrent = Math.max(0, actualCurrent);

                    totalActiveCurrent += actualCurrent;
                    totalNominalCurrentOfActives += motor.getNominalCurrent();
                    activeProductiveMotorsCount++;
                    activeMotorNames.add(motor.getName());
                }
            }

            double overallEfficiency = (totalNominalCurrentOfActives > 0)
                    ? totalActiveCurrent / totalNominalCurrentOfActives : 0;

            return new ProductiveEfficiencySnapshot(overallEfficiency, totalActiveCurrent,
                    totalNominalCurrentOfActives, activeProductiveMotorsCount, activeMotorNames);
        }

        public EnergyEfficiencySnapshot calculateEnergyEfficiency() {
            // ... (lógica existente, sem alterações)
            double totalActiveCurrent = 0;
            double totalNominalCurrentOfActives = 0;
            int activeMotorsCount = 0;
            List<String> activeMotorNames = new java.util.ArrayList<>();

            for (MotorDef motor : motorCatalog.getMotors()) {
                boolean isActive = modbusService.get(motor.getCcm(), motor.getStatusTagName())
                        .map(tag -> tag.getValue() > 0).orElse(false);

                if (isActive) {
                    double valueFromPlc = modbusService.get(motor.getCcm(), motor.getCurrentTagName())
                            .map(TagValue::getValue).orElse(0.0);

                    double actualCurrent = motor.isHasInverter()
                            ? motor.getNominalCurrent() - valueFromPlc
                            : valueFromPlc;
                    actualCurrent = Math.max(0, actualCurrent);

                    totalActiveCurrent += actualCurrent;
                    totalNominalCurrentOfActives += motor.getNominalCurrent();
                    activeMotorsCount++;
                    activeMotorNames.add(motor.getName());
                }
            }

            double overallLoadPercentage = (totalNominalCurrentOfActives > 0)
                    ? totalActiveCurrent / totalNominalCurrentOfActives : 0;

            return new EnergyEfficiencySnapshot(overallLoadPercentage, totalActiveCurrent,
                    totalNominalCurrentOfActives, activeMotorsCount, activeMotorNames);
        }
    }

    // --- DTOs ---

    @Data
    @AllArgsConstructor
    public static class ProductiveEfficiencySnapshot {
        private double overallEfficiency;
        private double totalActiveCurrent;
        private double totalNominalCurrentOfActives;
        private int activeProductiveMotorsCount;
        private List<String> activeMotorNames;
    }

    @Data
    @AllArgsConstructor
    public static class EnergyEfficiencySnapshot {
        private double overallLoadPercentage;
        private double totalActiveCurrent;
        private double totalNominalCurrentOfActives;
        private int activeMotorsCount;
        private List<String> activeMotorNames;
    }

    @Data
    @AllArgsConstructor
    public static class IndividualLoadSnapshot {
        private String name;
        private String ccm;
        private String status;
        private double loadPercentage;
        private double actualCurrent;
        private double nominalCurrent;
    }
}
