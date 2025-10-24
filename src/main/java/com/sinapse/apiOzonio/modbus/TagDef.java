package com.sinapse.apiOzonio.modbus;

import lombok.Data;

@Data
public class TagDef {
    private String name;
    private TagType type;   // <-- agora usa TagType
    private int address;
    private Integer unitId; // opcional
    private Double scale = 1.0; // default 1.0 pra evitar NPE
    private boolean writable;
}
