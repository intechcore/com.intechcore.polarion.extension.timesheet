package com.intechcore.polarion.extension.timesheet.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestValidatorTest {

    private static final String A_DAY = "2026-08-01";

    // --- The period ---

    @Test
    void period_acceptsAnOrderedIsoRange() {
        assertThatCode(() -> RequestValidator.validatePeriod(A_DAY, "2026-08-31")).doesNotThrowAnyException();
        assertThatCode(() -> RequestValidator.validatePeriod(A_DAY, A_DAY)).doesNotThrowAnyException();
    }

    @Test
    void period_needsBothDates() {
        assertThatThrownBy(() -> RequestValidator.validatePeriod(null, "2026-08-31"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date and end date are required");
        assertThatThrownBy(() -> RequestValidator.validatePeriod(A_DAY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date and end date are required");
    }

    /** The dates are concatenated into the Lucene query, so only a calendar date passes. */
    @ParameterizedTest
    @ValueSource(strings = {"2026-8-1", "20260801", "2026-13-01", "2026-02-30", "yesterday", "",
            "2026-08-01] OR project.id:[* TO *"})
    void period_rejectsWhatIsNotAnIsoDate(String date) {
        assertThatThrownBy(() -> RequestValidator.validatePeriod(date, "2026-12-31"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date must be an ISO date (yyyy-MM-dd)");
        assertThatThrownBy(() -> RequestValidator.validatePeriod(A_DAY, date))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End date must be an ISO date (yyyy-MM-dd)");
    }

    @Test
    void period_rejectsAnEndBeforeItsStart() {
        assertThatThrownBy(() -> RequestValidator.validatePeriod("2026-08-31", A_DAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End date must not precede start date");
    }

    /** A leap year fits, one day more does not: both days of the period belong to the report. */
    @Test
    void period_endsAtAYear() {
        assertThatCode(() -> RequestValidator.validatePeriod("2024-01-01", "2024-12-31")).doesNotThrowAnyException();
        assertThatThrownBy(() -> RequestValidator.validatePeriod("2024-01-01", "2025-01-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Period must not exceed 366 days");
    }

    // --- The users ---

    @Test
    void userIds_splitTrimAndKeepTheirOrder() {
        assertThat(RequestValidator.validateUserIds(" aSeller , ,mTest, ")).containsExactly("aSeller", "mTest");
        assertThat(RequestValidator.validateUserIds("first.last@example.com")).containsExactly("first.last@example.com");
    }

    /** An empty list used to report every user of the scope, which is a query over the repository. */
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", ",", " , "})
    void userIds_needAtLeastOne(String userIds) {
        assertThatThrownBy(() -> RequestValidator.validateUserIds(userIds))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one user id is required");
        assertThatThrownBy(() -> RequestValidator.validateUserIds(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one user id is required");
    }

    @Test
    void userIds_endAtFifty() {
        assertThatCode(() -> RequestValidator.validateUserIds(userList(50))).doesNotThrowAnyException();
        assertThatThrownBy(() -> RequestValidator.validateUserIds(userList(51)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No more than 50 user ids are allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a b", "a:b", "a*", "aSeller OR workRecords.user.id:mTest", "a\"b", "a(b)", "ä"})
    void userId_rejectsQuerySyntaxAndWhitespace(String userId) {
        assertThatThrownBy(() -> RequestValidator.validateUserId(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User id holds characters which are not allowed");
        assertThatThrownBy(() -> RequestValidator.validateUserIds("aSeller," + userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User id holds characters which are not allowed");
    }

    @Test
    void userId_rejectsNothingAndTooMuch() {
        assertThatThrownBy(() -> RequestValidator.validateUserId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RequestValidator.validateUserId("a".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(RequestValidator.validateUserId("a".repeat(64))).hasSize(64);
    }

    // --- The scope ---

    @Test
    void scopePath_takesAProjectAGroupAndNothing() {
        assertThat(RequestValidator.validateScopePath("elibrary")).isEqualTo("elibrary");
        assertThat(RequestValidator.validateScopePath(" /drafts/inner ")).isEqualTo("/drafts/inner");
        assertThat(RequestValidator.validateScopePath("/")).isEqualTo("/");
        assertThat(RequestValidator.validateScopePath("")).isEmpty();
        assertThat(RequestValidator.validateScopePath(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"elibrary OR project.id:secret", "a b", "a:b", "a*", "a\"b"})
    void scopePath_rejectsQuerySyntax(String scopePath) {
        assertThatThrownBy(() -> RequestValidator.validateScopePath(scopePath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Scope path holds characters which are not allowed");
    }

    @Test
    void scopePath_endsAtItsLength() {
        assertThat(RequestValidator.validateScopePath("a".repeat(256))).hasSize(256);
        assertThatThrownBy(() -> RequestValidator.validateScopePath("a".repeat(257)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String userList(int count) {
        List<String> ids = IntStream.range(0, count).mapToObj("user%d"::formatted).toList();
        return ids.stream().collect(Collectors.joining(","));
    }
}
