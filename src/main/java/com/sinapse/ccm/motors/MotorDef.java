package com.sinapse.ccm.motors;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MotorDef {
    private String name;
    private String ccm;
    private String category;

    @JsonProperty("nominalCurrent")
    private double nominalCurrent;

    @JsonProperty("statusTagName")
    private String statusTagName;

    @JsonProperty("secondaryStatusTagName")
    private String secondaryStatusTagName; // Para motores com reversão (ex: M85)

    @JsonProperty("currentTagName")
    private String currentTagName;

    @JsonProperty("power1TagName")
    private String power1TagName;

    @JsonProperty("power2TagName")
    private String power2TagName;

    @JsonProperty("faultTagName")
    private String faultTagName; // Tag de falha (ex: M1_F)

    @JsonProperty("hoursTagName")
    private String hoursTagName; // Tag de horímetro (ex: M1_H)

    @JsonProperty("hasInverter")
    private boolean hasInverter = false;
}
