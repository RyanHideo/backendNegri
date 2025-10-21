package com.sinapse.apiOzonio.modbus;

import lombok.Data;

@Data
public class TagDef {
    public enum Kind { HR, IR, COIL, DI }
    private String name;
    private Kind type;
    private int address;      // endereço modbus
    private int unitId = 1;
    private double scale = 1; // multiplicador
    private boolean writable = false;
}