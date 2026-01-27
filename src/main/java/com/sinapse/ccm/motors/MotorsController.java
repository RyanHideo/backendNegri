package com.sinapse.ccm.motors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/motors")
public class MotorsController {

    private final MotorService motors;

    public MotorsController(MotorService motors) {
        this.motors = motors;
    }

    @GetMapping
    public List<MotorDTO> list() {
        return motors.listAll();
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        List<MotorDTO> list = motors.listAll();

        long ativos = list.stream().filter(m -> m.status() == MotorStatus.ON).count();
        long falha = list.stream().filter(m -> m.status() == MotorStatus.ALARM).count();
        long desligados = list.stream().filter(m -> m.status() == MotorStatus.OFF).count();

        return Map.of(
                "total", list.size(),
                "ativos", ativos,
                "falha", falha,
                "desligados", desligados
        );
    }
}
