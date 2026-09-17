package com.intechcore.polarion.extension.timesheet.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks what the report endpoints receive, before any of it reaches a Polarion query.
 *
 * <p>Two reasons. The values are concatenated into the Lucene query the report runs, so a value
 * carrying query syntax would change the query instead of filling it. And an open request - every
 * user, every project, no end to the period - makes Polarion load the work records of the whole
 * repository, which is a single request against the availability of the server.
 *
 * <p>Every rejection is an {@link IllegalArgumentException}, which the generic extension answers
 * with 400. A message names the parameter and never quotes its value.
 */
@UtilityClass
public class RequestValidator {

    /** A full year, leap day included. */
    public static final int MAX_PERIOD_DAYS = 366;

    /** The report draws one block per user, and the picker offers no more than a team. */
    public static final int MAX_USER_IDS = 50;

    /**
     * The id of a user. The set is wider than the ids Polarion itself creates, because an
     * LDAP-backed installation can carry an e-mail-like login, and it still holds no character
     * the Lucene query syntax gives a meaning to.
     */
    private static final Pattern USER_ID = Pattern.compile("[A-Za-z0-9._@-]{1,64}");

    /** A project id, or the location path of a project group, which adds the separator. */
    private static final Pattern SCOPE_PATH = Pattern.compile("[A-Za-z0-9._/-]{1,256}");

    /**
     * Checks that the period is complete, ordered and bounded.
     *
     * @param startDate the first day of the report, as {@code yyyy-MM-dd}
     * @param endDate the last day of the report, as {@code yyyy-MM-dd}
     */
    public static void validatePeriod(@Nullable String startDate, @Nullable String endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        LocalDate start = parseDate(startDate, "Start date");
        LocalDate end = parseDate(endDate, "End date");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date must not precede start date");
        }
        // Both days belong to the report, so a period of one day spans zero days between its ends.
        if (ChronoUnit.DAYS.between(start, end) >= MAX_PERIOD_DAYS) {
            throw new IllegalArgumentException("Period must not exceed " + MAX_PERIOD_DAYS + " days");
        }
    }

    /**
     * Splits the user list of a request and checks every id in it.
     *
     * @param userIds the ids as one comma separated parameter
     * @return the ids, trimmed, in the order given
     */
    public static @NotNull List<String> validateUserIds(@Nullable String userIds) {
        List<String> ids = userIds == null
                ? List.of()
                : Arrays.stream(userIds.split(",")).map(String::trim).filter(id -> !id.isEmpty()).toList();

        if (ids.isEmpty()) {
            // An empty list used to mean every user of the scope, which is the open request above.
            throw new IllegalArgumentException("At least one user id is required");
        }
        if (ids.size() > MAX_USER_IDS) {
            throw new IllegalArgumentException("No more than " + MAX_USER_IDS + " user ids are allowed");
        }
        ids.forEach(RequestValidator::validateUserId);
        return ids;
    }

    /**
     * Checks one user id.
     *
     * @param userId the id to check
     * @return the id, unchanged
     */
    public static @NotNull String validateUserId(@Nullable String userId) {
        if (userId == null || !USER_ID.matcher(userId).matches()) {
            throw new IllegalArgumentException("User id holds characters which are not allowed");
        }
        return userId;
    }

    /**
     * Checks the scope of a request. No scope is the repository, which the report offers as well.
     *
     * @param scopePath a project id, a project group path, or null
     * @return the path, trimmed
     */
    public static @Nullable String validateScopePath(@Nullable String scopePath) {
        if (scopePath == null) {
            return null;
        }
        String trimmed = scopePath.trim();
        if (!trimmed.isEmpty() && !SCOPE_PATH.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Scope path holds characters which are not allowed");
        }
        return trimmed;
    }

    private static @NotNull LocalDate parseDate(@NotNull String value, @NotNull String name) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be an ISO date (yyyy-MM-dd)", e);
        }
    }
}
