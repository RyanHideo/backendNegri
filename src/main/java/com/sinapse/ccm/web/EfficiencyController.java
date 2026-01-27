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

import java.util.List;
import java.util.Optional;
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

    /**
     * Serviço principal para a lógica de cálculo de eficiência.
     */
    @Service
    @RequiredArgsConstructor
    public static class EfficiencyService {

        private final MotorCatalog motorCatalog;
        private final ModbusService modbusService;

        // Categorias de motores consideradas "produtivas"
        private static final Set<String> PRODUCTIVE_CATEGORIES = Set.of("elevador", "redler");

        public ProductiveEfficiencySnapshot calculateProductiveEfficiency() {
            double totalActiveCurrent = 0;
            double totalNominalCurrentOfActives = 0;
            int activeProductiveMotorsCount = 0;
            List<String> activeMotorNames = new java.util.ArrayList<>();

            // 1. Filtra apenas os motores que são da categoria produtiva
            List<MotorDef> productiveMotors = motorCatalog.getMotors().stream()
                    .filter(m -> PRODUCTIVE_CATEGORIES.contains(m.getCategory().toLowerCase()))
                    .toList();

            for (MotorDef motor : productiveMotors) {
                // 2. Verifica se o motor está ativo
                boolean isActive = modbusService.get(motor.getCcm(), motor.getStatusTagName())
                        .map(tag -> tag.getValue() > 0) // Considera ativo se o valor da tag de status for maior que 0
                        .orElse(false); // Se a tag não for encontrada, considera inativo

                if (isActive) {
                    // 3. Se estiver ativo, busca o valor da corrente atual
                    double current = modbusService.get(motor.getCcm(), motor.getCurrentTagName())
                            .map(TagValue::getValue)
                            .orElse(0.0); // Se não encontrar a tag de corrente, assume 0

                    // 4. Soma aos totais
                    totalActiveCurrent += current;
                    totalNominalCurrentOfActives += motor.getNominalCurrent();
                    activeProductiveMotorsCount++;
                    activeMotorNames.add(motor.getName());
                }
            }

            // 5. Calcula a eficiência geral
            double overallEfficiency = (totalNominalCurrentOfActives > 0)
                    ? totalActiveCurrent / totalNominalCurrentOfActives
                    : 0;

            return new ProductiveEfficiencySnapshot(
                    overallEfficiency,
                    totalActiveCurrent,
                    totalNominalCurrentOfActives,
                    activeProductiveMotorsCount,
                    activeMotorNames
            );
        }
    }

    /**
     * DTO para a resposta da API de eficiência.
     */
    @Data
    @AllArgsConstructor
    public static class ProductiveEfficiencySnapshot {
        private double overallEfficiency;
        private double totalActiveCurrent;
        private double totalNominalCurrentOfActives;
        private int activeProductiveMotorsCount;
        private List<String> activeMotorNames;
    }
}
