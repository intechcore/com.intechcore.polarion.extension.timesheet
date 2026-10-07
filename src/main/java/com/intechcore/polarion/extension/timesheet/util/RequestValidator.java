package com.intechcore.polarion.extension.timesheet.util;

import com.intechcore.polarion.extension.timesheet.model.ReportState;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Checks what the report endpoints receive, before any of it reaches a Polarion query.
 *
 * <p>It bounds a request: an open one - every user, every project, no end to the period - makes
 * Polarion load the work records of the whole repository, which is a single request against the
 * availability of the server. It does not filter characters. The query takes every value as a quoted
 * term (TimesheetReportManager), so no value can carry query syntax, and the controller checks that
 * each user and the scope exist.
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

    /** The longest user id Polarion keeps, with room for an LDAP login. */
    public static final int MAX_USER_ID_LENGTH = 64;

    /** The longest scope: a project id, or the location path of a nested group. */
    public static final int MAX_SCOPE_PATH_LENGTH = 256;

    // The key a widget gives its report: a SHA-256 in lowercase hex (TimesheetReportWidgetRenderer).
    private static final int STATE_KEY_LENGTH = 64;

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
     * Checks one user id: present and bounded. Its characters need no check: the query takes it as a
     * quoted term (TimesheetReportManager), and the controller checks that the user exists.
     *
     * @param userId the id to check
     * @return the id, unchanged
     */
    public static @NotNull String validateUserId(@Nullable String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required");
        }
        if (userId.length() > MAX_USER_ID_LENGTH) {
            throw new IllegalArgumentException("User id must not exceed " + MAX_USER_ID_LENGTH + " characters");
        }
        return userId;
    }

    /**
     * Checks the scope of a request. No scope is the repository, which the report offers as well.
     * Its characters need no check: a project id goes into the query as a quoted term, a group path
     * never reaches the query, and the controller checks that the project or the group exists.
     *
     * @param scopePath a project id, a project group path, or null
     * @return the path, trimmed
     */
    public static @Nullable String validateScopePath(@Nullable String scopePath) {
        if (scopePath == null) {
            return null;
        }
        String trimmed = scopePath.trim();
        if (trimmed.length() > MAX_SCOPE_PATH_LENGTH) {
            throw new IllegalArgumentException("Scope path must not exceed " + MAX_SCOPE_PATH_LENGTH + " characters");
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

    /**
     * Checks the key under which a report keeps its selection.
     *
     * @param stateKey the key the widget passed to its report
     * @return the key, unchanged
     */
    public static @NotNull String validateStateKey(@Nullable String stateKey) {
        boolean valid = stateKey != null && stateKey.length() == STATE_KEY_LENGTH
                && stateKey.chars().allMatch(c -> HexFormat.isHexDigit(c) && !Character.isUpperCase(c));
        if (!valid) {
            throw new IllegalArgumentException("State key is not valid");
        }
        return stateKey;
    }

    /**
     * Checks a selection a report sends, by the rules of the timesheet request it stands for.
     *
     * @param state the selection
     * @return the selection, with the scope path and the user ids trimmed
     */
    public static @NotNull ReportState validateReportState(@Nullable ReportState state) {
        if (state == null) {
            throw new IllegalArgumentException("Report state is required");
        }
        validatePeriod(state.getStartDate(), state.getEndDate());
        String scopePath = validateScopePath(state.getScopePath());
        List<String> userIds = validateUserIds(state.getUserIds());
        return new ReportState(scopePath, String.join(",", userIds), state.getStartDate(), state.getEndDate());
    }
}
