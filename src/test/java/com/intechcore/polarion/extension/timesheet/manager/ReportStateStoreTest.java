package com.intechcore.polarion.extension.timesheet.manager;

import com.intechcore.polarion.extension.timesheet.model.ReportState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ReportStateStoreTest {

    private static final ReportState JUNE = new ReportState("/", "aSeller", "2026-06-01", "2026-06-30");

    /** A clock the test moves forward by hand. */
    private static final class Hand extends Clock {
        private Instant now = Instant.parse("2026-10-07T10:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void keepsOneSelectionPerUserAndReport() {
        ReportStateStore store = new ReportStateStore(new Hand());
        ReportState july = new ReportState("/", "aSeller", "2026-07-01", "2026-07-31");

        store.save("aSeller", "key", JUNE);
        store.save("mTest", "key", july);

        assertThat(store.find("aSeller", "key")).isEqualTo(JUNE);
        assertThat(store.find("mTest", "key")).isEqualTo(july);
        assertThat(store.find("aSeller", "other")).isNull();
        // The last selection replaces the one before it.
        store.save("aSeller", "key", july);
        assertThat(store.find("aSeller", "key")).isEqualTo(july);
    }

    @Test
    void forgetsASelectionAfterADay() {
        Hand clock = new Hand();
        ReportStateStore store = new ReportStateStore(clock);
        store.save("aSeller", "key", JUNE);

        clock.now = clock.now.plus(ReportStateStore.TIME_TO_LIVE).plusSeconds(1);

        assertThat(store.find("aSeller", "key")).isNull();
        // The next save clears what expired.
        store.save("mTest", "key", JUNE);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void dropsTheOldestSelectionAboveTheBound() {
        Hand clock = new Hand();
        ReportStateStore store = new ReportStateStore(clock);
        for (int i = 0; i <= ReportStateStore.MAX_ENTRIES; i++) {
            store.save("user" + i, "key", JUNE);
            clock.now = clock.now.plusMillis(1);
        }

        assertThat(store.size()).isEqualTo(ReportStateStore.MAX_ENTRIES);
        assertThat(store.find("user0", "key")).isNull();
        assertThat(store.find("user" + ReportStateStore.MAX_ENTRIES, "key")).isEqualTo(JUNE);
    }

    @Test
    void isOneStoreForTheWholeExtension() {
        assertThat(ReportStateStore.getInstance()).isSameAs(ReportStateStore.getInstance());
    }
}
