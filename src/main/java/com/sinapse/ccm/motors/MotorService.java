package com.sinapse.ccm.motors;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MotorService {

    private final ModbusService modbus;

    public MotorService(ModbusService modbus) {
        this.modbus = modbus;
    }

    public List<MotorDTO> listAll() {
        Map<String, TagValue> snap = modbus.snapshot();

        List<MotorDTO> list = new ArrayList<>();

        // TODO: aqui você mapeia tags -> motores de verdade.
        // Por enquanto vou deixar exemplos “hardcoded” pra você trocar depois.

        list.add(buildMotor(
                "Motor 101",
                "Esteira alimentação moinho",
                MotorStatus.OFF,
                0.0,
                116.0,
                "OK"
        ));

        list.add(buildMotor(
                "Motor 102",
                "Esteira alimentação peneira",
                MotorStatus.OFF,
                42.0,
                88.0,
                "OK"
        ));

        list.add(buildMotor(
                "Motor 103",
                "Esteira descarga de pó",
                MotorStatus.ALARM,
                95.0,
                170.0,
                "Sobrecarga"
        ));

        return list;
    }

    private MotorDTO buildMotor(
            String id,
            String name,
            MotorStatus status,
            double loadPct,
            double hours,
            String alarmText
    ) {
        return new MotorDTO(id, name, status, loadPct, hours, alarmText);
    }
}
