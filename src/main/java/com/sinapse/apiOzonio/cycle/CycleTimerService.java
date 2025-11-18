package com.sinapse.apiOzonio.cycle;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class CycleTimerService {

    /** Duração total do ciclo em ms (padrão: 12h). Configurável em application.yml */
    @Value("${cycle.total-ms:43200000}") // 12L * 3600L * 1000L
    private long totalMs;

    /** Momento (epoch ms) em que o ciclo iniciou; null = parado */
    private final AtomicReference<Long> startAt = new AtomicReference<>(null);

    /** Chamada a cada polling para refletir o bit do CLP (true = iniciou, false = parou) */
    public synchronized void onCicloBit(boolean on) {
        Long cur = startAt.get();
        if (on && cur == null) {
            long now = System.currentTimeMillis();
            startAt.set(now);
            log.info("Cycle START at {}", now);
        } else if (!on && cur != null) {
            startAt.set(null);
            log.info("Cycle STOP (reset clock to full)");
        }
    }

    /** Snapshot para API/Front */
    public synchronized CycleSnapshot snapshot() {
        long now = System.currentTimeMillis();
        Long s = startAt.get();
        boolean running = (s != null);
        long elapsed = running ? Math.max(0, now - s) : 0L;
        long remaining = running ? Math.max(0, totalMs - elapsed) : totalMs;
        double progress = (totalMs == 0) ? 0.0 : Math.min(1.0, (double) elapsed / (double) totalMs);

        return new CycleSnapshot(totalMs, s, running, elapsed, remaining, progress);
    }

    @Getter
    public static class CycleSnapshot {
        private final long  totalMs;
        private final Long  startAt;     // epoch ms (null se parado)
        private final boolean running;
        private final long  elapsedMs;
        private final long  remainingMs;
        private final double progress;   // 0..1

        public CycleSnapshot(long totalMs, Long startAt, boolean running, long elapsedMs, long remainingMs, double progress) {
            this.totalMs = totalMs;
            this.startAt = startAt;
            this.running = running;
            this.elapsedMs = elapsedMs;
            this.remainingMs = remainingMs;
            this.progress = progress;
        }
    }
}
