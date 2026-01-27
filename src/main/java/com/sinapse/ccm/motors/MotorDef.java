package com.sinapse.ccm.motors;

import lombok.Data;

@Data
public class MotorDef {
    private String name;
    private String ccm;
    private String category;
    private double nominalCurrent;
    private String statusTagName;
    private String currentTagName;
}
