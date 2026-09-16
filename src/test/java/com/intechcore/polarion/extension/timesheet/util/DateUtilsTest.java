package com.intechcore.polarion.extension.timesheet.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DateUtilsTest {

    private static Date date(int year, int month, int day) {
        return DateUtils.convertToDate(LocalDate.of(year, month, day));
    }

    @Test
    void getTimesheetTableDate_formatsAsDayDotMonth() {
        assertThat(DateUtils.getTimesheetTableDate(date(2026, 6, 1))).isEqualTo("01.06");
        assertThat(DateUtils.getTimesheetTableDate(date(2026, 12, 31))).isEqualTo("31.12");
    }

    @Test
    void getPolarionQueryDate_formatsAsCompact() {
        assertThat(DateUtils.getPolarionQueryDate(date(2026, 6, 1))).isEqualTo("20260601");
    }

    @Test
    void getAllDatesInPeriod_isInclusiveOnBothEnds() {
        List<Date> dates = DateUtils.getAllDatesInPeriod(date(2026, 6, 1), date(2026, 6, 3));
        assertThat(dates).hasSize(3);
        assertThat(DateUtils.getPolarionQueryDate(dates.get(0))).isEqualTo("20260601");
        assertThat(DateUtils.getPolarionQueryDate(dates.get(2))).isEqualTo("20260603");
    }

    @Test
    void getAllDatesInPeriod_singleDay() {
        assertThat(DateUtils.getAllDatesInPeriod(date(2026, 6, 1), date(2026, 6, 1))).hasSize(1);
    }

    @Test
    void isWeekend_detectsSaturdayAndSunday() {
        assertThat(DateUtils.isWeekend(date(2026, 1, 3))).isTrue();  // Saturday
        assertThat(DateUtils.isWeekend(date(2026, 1, 4))).isTrue();  // Sunday
        assertThat(DateUtils.isWeekend(date(2026, 1, 5))).isFalse(); // Monday
    }

    @Test
    void convertRoundTrip() {
        LocalDate local = LocalDate.of(2026, 6, 15);
        assertThat(DateUtils.convertToLocalDate(DateUtils.convertToDate(local))).isEqualTo(local);
    }
}
