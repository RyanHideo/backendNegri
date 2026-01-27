package com.sinapse.ccm.web;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/modbus")
@CrossOrigin(origins = "*") // Para desenvolvimento, permite acesso de qualquer origem
public class ModbusController {

    private final ModbusService modbusService;

    /**
     * Retorna o snapshot mais recente de todas as tags para um CCM específico.
     * Exemplo: GET /api/modbus/ccm1/tags
     * @param ccmKey "ccm1" ou "ccm2"
     * @return Um mapa com nome da tag -> valor da tag.
     */
    @GetMapping("/{ccmKey}/tags")
    public Map<String, TagValue> getTags(@PathVariable String ccmKey) {
        return modbusService.snapshot(ccmKey);
    }

    /**
     * Retorna o valor de uma tag específica.
     * Exemplo: GET /api/modbus/ccm1/tags/NOME_DA_TAG
     * @param ccmKey "ccm1" ou "ccm2"
     * @param tagName O nome da tag a ser lida.
     * @return O valor da tag ou 404 Not Found se não existir.
     */
    @GetMapping("/{ccmKey}/tags/{tagName}")
    public ResponseEntity<TagValue> getTag(@PathVariable String ccmKey, @PathVariable String tagName) {
        return modbusService.get(ccmKey, tagName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DTO para a requisição de escrita.
     */
    @Data
    public static class WriteRequest {
        private String name;
        private int value;
    }

    /**
     * Escreve um valor "raw" em uma tag (HR ou COIL).
     * Exemplo: POST /api/modbus/ccm1/write
     * Body: { "name": "NOME_DA_TAG", "value": 1 }
     * @param ccmKey "ccm1" ou "ccm2"
     * @param request O corpo da requisição com 'name' e 'value'.
     * @return Resposta de sucesso ou erro.
     */
    @PostMapping("/{ccmKey}/write")
    public ResponseEntity<?> writeTag(@PathVariable String ccmKey, @RequestBody WriteRequest request) {
        try {
            modbusService.write(ccmKey, request.getName(), request.getValue());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Aciona o comando de RESET por pulso.
     * Exemplo: POST /api/modbus/ccm1/reset
     * @param ccmKey "ccm1" ou "ccm2"
     * @return Resposta de sucesso ou erro.
     */
    @PostMapping("/{ccmKey}/reset")
    public ResponseEntity<?> reset(@PathVariable String ccmKey) {
        try {
            modbusService.resetPulse(ccmKey, 200); // Duração padrão de 200ms
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Aciona o comando de EMERGÊNCIA (retentivo).
     * Exemplo: POST /api/modbus/ccm1/emergency
     * @param ccmKey "ccm1" ou "ccm2"
     * @return Resposta de sucesso ou erro.
     */
    @PostMapping("/{ccmKey}/emergency")
    public ResponseEntity<?> emergency(@PathVariable String ccmKey) {
        try {
            modbusService.latchEmergency(ccmKey);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
