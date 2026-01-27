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

    /**
     * Endpoint para o front-end acionar o reset do consumo para um CCM específico.
     * @param ccmKey "ccm1" ou "ccm2"
     */
    @PostMapping("/{ccmKey}/reset")
    public ResponseEntity<String> resetConsumption(@PathVariable String ccmKey) {
        try {
            // Envia o código de comando para o registrador de comando do CLP
            modbusService.write(ccmKey, CMD_REGISTER_TAG, RESET_CONSUMPTION_CODE);

            // Atualiza e persiste a data do reset para o CCM específico
            stateService.updateAndPersistResetTimestamp(ccmKey);

            return ResponseEntity.ok("Comando de reset de consumo enviado para " + ccmKey + " e data atualizada.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Falha ao enviar comando para " + ccmKey + ": " + e.getMessage());
        }
    }

    /**
     * Endpoint para o front-end buscar a data do último reset de um CCM específico.
     * @param ccmKey "ccm1" ou "ccm2"
     */
    @GetMapping("/{ccmKey}/reset-date")
    public ResponseEntity<ResetDateResponse> getResetDate(@PathVariable String ccmKey) {
        Instant timestamp = stateService.getLastResetTimestamp(ccmKey);
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
