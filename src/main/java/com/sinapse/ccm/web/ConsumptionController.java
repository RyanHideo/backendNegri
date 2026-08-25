package com.sinapse.ccm.web;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.state.StateService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/consumption")
@CrossOrigin(origins = "*")
public class ConsumptionController {

    private final StateService stateService;
    private final ModbusService modbusService;

    private static final String CMD_REGISTER_TAG = "CMD_REGISTER";
    private static final int RESET_CONSUMPTION_CODE = 999; // Código para zerar consumo total

    @PostMapping({"/reset", "/ccm1/reset"})
    public ResponseEntity<String> resetConsumption() {
        try {
            modbusService.write(CMD_REGISTER_TAG, RESET_CONSUMPTION_CODE);

            stateService.updateAndPersistResetTimestamp();

            return ResponseEntity.ok("Comando de reset de consumo enviado para o CCM e data atualizada.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Falha ao enviar comando para o CCM: " + e.getMessage());
        }
    }

    @GetMapping({"/reset-date", "/ccm1/reset-date"})
    public ResponseEntity<ResetDateResponse> getResetDate() {
        Instant timestamp = stateService.getLastResetTimestamp();
        return ResponseEntity.ok(new ResetDateResponse(timestamp));
    }

    /**
     * DTO para a resposta da data de reset.
     */
    @Data
    @AllArgsConstructor
    public static class ResetDateResponse {
        private Instant lastResetTimestamp;
    }
}
