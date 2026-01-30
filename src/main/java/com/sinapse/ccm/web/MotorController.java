package com.sinapse.ccm.web;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import com.sinapse.ccm.motors.MotorCatalog;
import com.sinapse.ccm.motors.MotorDef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/motors")
@CrossOrigin(origins = "*")
public class MotorController {

    private final MotorCatalog motorCatalog;
    private final ModbusService modbusService;
    private static final String CMD_REGISTER_TAG = "CMD_REGISTER";

    @GetMapping("/overview")
    public List<MotorOverviewDto> getMotorsOverview() {
        return motorCatalog.getMotors().stream()
                .map(this::buildDtoForMotor)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/overview/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMotorsOverview() {
        SseEmitter emitter = new SseEmitter(0L);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {
            try {
                List<MotorOverviewDto> overview = getMotorsOverview();
                emitter.send(SseEmitter.event().data(overview));
            } catch (IOException e) {
                emitter.complete();
                executor.shutdown();
            } catch (Exception e) {
                emitter.completeWithError(e);
                executor.shutdown();
            }
        }, 0, 1, TimeUnit.SECONDS);

        emitter.onCompletion(executor::shutdown);
        emitter.onTimeout(executor::shutdown);

        return emitter;
    }

    @PostMapping("/reset-hourmeter")
    public ResponseEntity<?> resetHourmeter(@RequestBody ResetHourmeterRequest request) {
        Optional<MotorDef> motorOpt = motorCatalog.getMotors().stream()
                .filter(m -> m.getName().equals(request.getMotorName()) && m.getCcm().equals(request.getCcm()))
                .findFirst();

        if (motorOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Motor não encontrado: " + request.getMotorName() + " no CCM " + request.getCcm());
        }

        MotorDef motor = motorOpt.get();
        
        Pattern pattern = Pattern.compile("M(\\d+)_S");
        Matcher matcher = pattern.matcher(motor.getStatusTagName());

        if (!matcher.find()) {
            return ResponseEntity.badRequest().body("Não foi possível determinar o ID numérico do motor a partir da tag: " + motor.getStatusTagName());
        }

        try {
            int motorId = Integer.parseInt(matcher.group(1));
            int commandValue = motorId + 100;

            log.info("Reset Horímetro: Motor ID extraído = {}, Valor de comando enviado ao CLP = {}", motorId, commandValue);

            modbusService.write(motor.getCcm(), CMD_REGISTER_TAG, commandValue);
            
            return ResponseEntity.ok().body("Comando para zerar horímetro do motor " + motorId + " (código " + commandValue + ") enviado para " + motor.getCcm());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("Falha ao extrair ID numérico da tag: " + motor.getStatusTagName());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Falha ao enviar comando: " + e.getMessage());
        }
    }

    private MotorOverviewDto buildDtoForMotor(MotorDef motor) {
        Double status = getValue(motor.getCcm(), motor.getStatusTagName()).orElse(0.0);
        
        // Lógica para motor reversível (M85)
        if (motor.getSecondaryStatusTagName() != null && !motor.getSecondaryStatusTagName().isBlank()) {
            Double status2 = getValue(motor.getCcm(), motor.getSecondaryStatusTagName()).orElse(0.0);
            // Se qualquer um dos dois estiver ligado (> 0), consideramos ligado
            if (status2 > 0) {
                status = status2; 
            }
        }

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

    @Data
    public static class ResetHourmeterRequest {
        private String motorName;
        private String ccm;
    }
}
