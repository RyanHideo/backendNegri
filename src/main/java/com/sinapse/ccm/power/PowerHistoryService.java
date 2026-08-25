package com.sinapse.ccm.power;

import com.sinapse.ccm.modbus.ModbusService;
import com.sinapse.ccm.modbus.TagValue;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PowerHistoryService {

    static final String MAIN_TRANSFORMER_TAG = "KW";
    static final String VSI_TRANSFORMER_TAG = "PotenciaTrafoVsiKva";
    static final String GENERAL_TAG = "PotenciaGeralKva";

    private final ModbusService modbusService;
    private final Deque<PowerPoint> mainTransformer = new ArrayDeque<>();
    private final Deque<PowerPoint> vsiTransformer = new ArrayDeque<>();
    private final Deque<PowerPoint> general = new ArrayDeque<>();

    @Value("${power.history.size:10}")
    private int historySize = 10;

    @Scheduled(
            fixedDelayString = "${power.history.poll-ms:1000}",
            initialDelayString = "${power.history.initial-delay-ms:1500}"
    )
    public synchronized void capture() {
        Map<String, TagValue> snapshot = modbusService.snapshot();
        Instant capturedAt = Instant.now();
        append(mainTransformer, snapshot.get(MAIN_TRANSFORMER_TAG), capturedAt);
        append(vsiTransformer, snapshot.get(VSI_TRANSFORMER_TAG), capturedAt);
        append(general, snapshot.get(GENERAL_TAG), capturedAt);
    }

    public synchronized PowerHistorySnapshot snapshot() {
        return new PowerHistorySnapshot(
                new ArrayList<>(mainTransformer),
                new ArrayList<>(vsiTransformer),
                new ArrayList<>(general)
        );
    }

    private void append(Deque<PowerPoint> history, TagValue reading, Instant capturedAt) {
        if (reading == null
                || reading.getQuality() != TagValue.Quality.GOOD
                || !Double.isFinite(reading.getValue())) {
            return;
        }

        history.addLast(new PowerPoint(capturedAt, reading.getValue(), reading.getQuality()));
        int limit = Math.max(1, historySize);
        while (history.size() > limit) {
            history.removeFirst();
        }
    }

    public record PowerPoint(Instant timestamp, double value, TagValue.Quality quality) {
    }

    public record PowerHistorySnapshot(
            List<PowerPoint> mainTransformer,
            List<PowerPoint> vsiTransformer,
            List<PowerPoint> general
    ) {
    }
}
