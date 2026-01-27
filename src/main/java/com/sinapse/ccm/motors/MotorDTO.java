package com.sinapse.ccm.motors;


public record MotorDTO(
        String id,          // ex: "Motor 101"
        String name,        // ex: "Esteira alimentação peneira"
        MotorStatus status, // ON / OFF / ALARM
        double loadPct,     // % de carga (0–100)
        double hours,       // horímetro (h)
        String alarmText    // "OK", "Sobrecarga", etc.
) {}
