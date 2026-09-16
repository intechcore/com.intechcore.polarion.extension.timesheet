package com.intechcore.polarion.extension.timesheet.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@UtilityClass
public class DateUtils {

    public static final String DD_MM = "dd.MM";
    public static final String YYYY_MM_DD = "yyyyMMdd";

    public static @NotNull String getTimesheetTableDate(@NotNull Date date) {
        return formatDate(date, DD_MM);
    }

    public static @NotNull String getPolarionQueryDate(@NotNull Date date) {
        return formatDate(date, YYYY_MM_DD);
    }

    public static @NotNull String formatDate(@NotNull Date date, @NotNull String format) {
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern(format));
    }

    public static @NotNull List<Date> getAllDatesInPeriod(@NotNull Date dateStart, @NotNull Date dateEnd) {
        LocalDate startDate = convertToLocalDate(dateStart);
        LocalDate endDate = convertToLocalDate(dateEnd);

        List<Date> dates = new ArrayList<>();

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            dates.add(Date.from(current.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            current = current.plusDays(1);
        }

        return dates;
    }

    public static @NotNull LocalDate convertToLocalDate(Date date) {
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    public static @NotNull Date convertToDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public static boolean isWeekend(@NotNull Date date) {
        LocalDate localDate = convertToLocalDate(date);
        return localDate.getDayOfWeek().getValue() > 5;
    }
}
