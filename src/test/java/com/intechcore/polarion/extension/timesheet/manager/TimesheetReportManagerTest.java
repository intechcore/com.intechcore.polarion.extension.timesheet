package com.intechcore.polarion.extension.timesheet.manager;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TimesheetReportManagerTest {

    private final TimesheetReportManager manager = new TimesheetReportManager(mock(PolarionService.class));

    @Test
    void isWithinPeriod_inclusiveRange() {
        assertThat(manager.isWithinPeriod("2026-06-15", "2026-06-01", "2026-06-30")).isTrue();
        assertThat(manager.isWithinPeriod("2026-06-01", "2026-06-01", "2026-06-30")).isTrue(); // start boundary
        assertThat(manager.isWithinPeriod("2026-06-30", "2026-06-01", "2026-06-30")).isTrue(); // end boundary
    }

    @Test
    void isWithinPeriod_outOfRange() {
        assertThat(manager.isWithinPeriod("2026-05-31", "2026-06-01", "2026-06-30")).isFalse();
        assertThat(manager.isWithinPeriod("2026-07-01", "2026-06-01", "2026-06-30")).isFalse();
    }

    @Test
    void isWithinPeriod_toleratesMixedDateFormats() {
        assertThat(manager.isWithinPeriod("2026-06-15", "20260601", "20260630")).isTrue();
    }

    @Test
    void extractIconUrl_returnsFirstImgSrc() {
        String html = "<span class=\"polarion-no-style-cleanup\"><a class=\"polarion-Hyperlink\" href=\"#\">"
                + "<span><img src=\"/polarion/ria/images/enums/type_heading.png\" class=\"polarion-Icons\"/></span>"
                + "EL-7<span> - Abbreviations</span></a></span>";
        assertThat(manager.extractIconUrl(html)).isEqualTo("/polarion/ria/images/enums/type_heading.png");
    }

    @Test
    void extractIconUrl_nullOrNoImage() {
        assertThat(manager.extractIconUrl(null)).isNull();
        assertThat(manager.extractIconUrl("<a href=\"#\">EL-1</a>")).isNull();
    }

    @Test
    void createWorkRecordsQuery_stripsDashes() {
        assertThat(manager.createWorkRecordsQuery("2026-06-01", "2026-06-30"))
                .isEqualTo("(workRecords.date:[20260601 TO 20260630])");
    }

    @Test
    void createUserWorkRecordsQuery_joinsWithOr() {
        assertThat(manager.createUserWorkRecordsQuery(List.of("aSeller", "mTest")))
                .isEqualTo("workRecords.user.id:aSeller OR workRecords.user.id:mTest");
        assertThat(manager.createUserWorkRecordsQuery(List.of("solo")))
                .isEqualTo("workRecords.user.id:solo");
    }
}
