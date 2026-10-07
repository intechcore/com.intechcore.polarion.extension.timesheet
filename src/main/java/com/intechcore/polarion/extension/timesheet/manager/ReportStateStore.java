package com.intechcore.polarion.extension.timesheet.manager;

import com.intechcore.polarion.extension.timesheet.model.ReportState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The selection each user last made in each widget report, for the PDF export of the page.
 *
 * <p>It lives in memory: a selection matters for the export that follows it, not across a restart.
 * An entry expires after a day, and the store keeps a bounded number of them, dropping the oldest.
 */
public final class ReportStateStore {

    static final Duration TIME_TO_LIVE = Duration.ofDays(1);
    static final int MAX_ENTRIES = 10_000;

    private static final ReportStateStore INSTANCE = new ReportStateStore(Clock.systemUTC());

    private record Entry(@NotNull ReportState state, @NotNull Instant savedAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    ReportStateStore(@NotNull Clock clock) {
        this.clock = clock;
    }

    public static @NotNull ReportStateStore getInstance() {
        return INSTANCE;
    }

    public void save(@NotNull String userId, @NotNull String stateKey, @NotNull ReportState state) {
        Instant now = clock.instant();
        entries.values().removeIf(entry -> isExpired(entry, now));
        entries.put(key(userId, stateKey), new Entry(state, now));
        while (entries.size() > MAX_ENTRIES) {
            entries.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().savedAt()))
                    .ifPresent(oldest -> entries.remove(oldest.getKey()));
        }
    }

    public @Nullable ReportState find(@NotNull String userId, @NotNull String stateKey) {
        Entry entry = entries.get(key(userId, stateKey));
        return entry == null || isExpired(entry, clock.instant()) ? null : entry.state();
    }

    int size() {
        return entries.size();
    }

    private boolean isExpired(@NotNull Entry entry, @NotNull Instant now) {
        return entry.savedAt().plus(TIME_TO_LIVE).isBefore(now);
    }

    // The NUL cannot appear in a user id nor in a key: the two parts never run into each other.
    private static @NotNull String key(@NotNull String userId, @NotNull String stateKey) {
        return userId + '\u0000' + stateKey;
    }
}
