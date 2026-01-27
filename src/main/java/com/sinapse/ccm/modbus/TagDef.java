package com.sinapse.ccm.modbus;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TagDef {

    private String  name;

    @JsonProperty("name_front") // Mapeia a propriedade do YAML para o campo Java
    private String  nameFront;     // Nome para exibição no front-end

    private TagType type;          // HR | IR | COIL | DI
    private Integer address;       // ex.: 4005, 1, 1001...
    private Integer unitId;        // opcional (default 1)
    private Double  scale;         // opcional (default 1.0)

    // Apenas para HR/IR quando 32 bits
    private Integer words;         // 1 (16-bit) ou 2 (32-bit). default 1
    private String  format;        // U16, U32, S32, F32 (default U16)
    private String  endian;        // BIG ou LITTLE (default BIG)

    // Atributos de acesso/comportamento
    private Boolean writable;      // default false (pode escrever no CLP?)
    private Boolean readable;      // default true  (faz sentido ler do CLP?)
    private Boolean polled;        // default true  (entra no ciclo de polling?)

    // Opcional: texto livre para documentação
    private String  description;

    // -------- Helpers com defaults --------
    public int getUnitIdOrDefault()      { return unitId   != null ? unitId   : 1; }
    public double getScaleOrDefault()    { return scale    != null ? scale    : 1.0; }
    public int getWordsOrDefault()       { return words    != null ? words    : 1; }
    public String getFormatOrDefault()   { return format   != null ? format   : "U16"; }
    public String getEndianOrDefault()   { return endian   != null ? endian   : "BIG"; }
    public boolean isWritableOrDefault() { return writable != null ? writable : false; }
    public boolean isReadableOrDefault() { return readable != null ? readable : true; }
    public boolean isPolledOrDefault()   { return polled   != null ? polled   : true; }
}
