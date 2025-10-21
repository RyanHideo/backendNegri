package com.sinapse.apiOzonio.modbus;

import lombok.Data;

@Data
public class TagDef {
    private String name;
    private TagType type;
    private int address;
    private int unitId;
    private double scale = 1.0;
    private boolean writable;
    private String description;
}
