package com.sinapse.ccm.auxiliary;

import java.time.Instant;

public record AuxiliaryEquipmentSnapshot(
        PrimaryCrusherSnapshot britadorPrimario,
        SemaphoreSnapshot semaforo,
        TruckCounterSnapshot contadorCaminhoes
) {
    public record PrimaryCrusherSnapshot(
            Double potenciaPercentual,
            String status,
            Instant updatedAt
    ) {
    }

    public record SemaphoreSnapshot(
            String estado,
            String status,
            Instant updatedAt
    ) {
    }

    public record TruckCounterSnapshot(
            Long contagem,
            Long meta,
            Instant ultimoPulso,
            String status
    ) {
    }
}
