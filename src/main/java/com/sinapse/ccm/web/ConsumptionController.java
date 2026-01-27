package com.sinapse.ccm.web;

import com.sinapse.ccm.state.StateService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/consumption")
@CrossOrigin(origins = "*")
public class ConsumptionController {

    private final StateService stateService;

    /**
     * Endpoint para o front-end acionar o reset do consumo.
     * Além de atualizar a data, ele também precisa enviar o comando para o CLP.
     */
    @PostMapping("/reset")
    public ResponseEntity<String> resetConsumption() {
        // Aqui, você também precisaria enviar o comando para o CLP zerar o acumulador.
        // Por exemplo, usando o ModbusService que já temos.
        // modbusService.write("ccm1", "COMANDO_ZERAR_CONSUMO", 1);

        // Atualiza e persiste a data do reset no arquivo.
        stateService.updateAndPersistResetTimestamp();

        return ResponseEntity.ok("Consumo resetado e data atualizada.");
    }

    /**
     * Endpoint para o front-end buscar a data do último reset.
     */
    @GetMapping("/reset-date")
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
