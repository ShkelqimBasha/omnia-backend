package com.omnia.backend.security.throttle;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window counter. The backend runs as one instance, so memory is
 * enough; a restart forgets the counts, which only loosens the limit for a moment.
 */
@Component
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 20_000;
    private static final Duration MAX_WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Records a hit and says whether it is within {@code limit} hits per {@code window}. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long since = now - window.toMillis();
        if (hits.size() > CLEANUP_THRESHOLD) {
            cleanup(now);
        }
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() <= since) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    /** Hits for {@code key} inside the window, without recording one. */
    public int count(String key, Duration window) {
        Deque<Long> times = hits.get(key);
        if (times == null) return 0;
        long since = clock.millis() - window.toMillis();
        synchronized (times) {
            return (int) times.stream().filter(t -> t > since).count();
        }
    }

    /** Milliseconds until the oldest hit in the window expires (0 when none). */
    public long millisUntilFree(String key, Duration window) {
        Deque<Long> times = hits.get(key);
        if (times == null) return 0;
        long since = clock.millis() - window.toMillis();
        synchronized (times) {
            for (Long time : times) {
                if (time > since) {
                    return Math.max(0, time + window.toMillis() - clock.millis());
                }
            }
            return 0;
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }

    private void cleanup(long now) {
        long since = now - MAX_WINDOW.toMillis();
        hits.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                Long last = entry.getValue().peekLast();
                return last == null || last <= since;
            }
        });
    }
}
