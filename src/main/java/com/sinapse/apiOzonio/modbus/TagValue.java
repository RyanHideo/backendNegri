package com.sinapse.apiOzonio.modbus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class TagValue {
    public enum Quality { GOOD, BAD, STALE }

    private final String name;
    private final double value;       // mantém double para HR e 0/1 para coils
    private final Instant ts;
    private final Quality quality;    // nova flag de qualidade
    private final String error;       // mensagem de erro da última leitura (opcional)
}
