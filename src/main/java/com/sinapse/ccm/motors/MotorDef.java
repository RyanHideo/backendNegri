package com.sinapse.ccm.motors;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MotorDef {
    private String name;

    /**
     * Campo legado mantido somente para carregar o motors.yml atual.
     * Não participa mais de filtros, seleção de catálogo ou acesso Modbus.
     */
    private String ccm;

    private String category;

    @JsonProperty("nominalCurrent")
    private double nominalCurrent;

    @JsonProperty("statusTagName")
    private String statusTagName;

    /**
     * Indica que a tag de status é uma palavra de estado da soft-starter WEG.
     * Nessa palavra, o bit 0 informa motor girando e o bit 15 informa falha.
     */
    @JsonProperty("softStarterStatusWord")
    private boolean softStarterStatusWord = false;

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
