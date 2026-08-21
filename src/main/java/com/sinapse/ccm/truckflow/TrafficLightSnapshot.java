package com.sinapse.ccm.truckflow;

import com.sinapse.ccm.modbus.TagValue;

import java.time.Instant;

public record TrafficLightSnapshot(
        TrafficLightStatus status,
        long truckCount,
        TagValue.Quality quality,
        Instant updatedAt
) {
    public static TrafficLightSnapshot unknown(long truckCount) {
        return new TrafficLightSnapshot(
                TrafficLightStatus.UNKNOWN,
                truckCount,
                TagValue.Quality.STALE,
                Instant.now()
        );
    }
}
