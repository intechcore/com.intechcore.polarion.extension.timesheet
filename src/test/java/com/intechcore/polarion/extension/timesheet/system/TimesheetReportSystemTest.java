package com.intechcore.polarion.extension.timesheet.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a running Polarion, never on CI: {@code mvn -Psystem-tests verify}.
 * <p>
 * The test prepares its own data and asserts what the report answers for it. The period is in 2030 so
 * the records cannot collide with real ones, and the work items carry a marker in their title. Work
 * records are deleted afterwards; work items are not, because Polarion's REST API refuses to delete a
 * work item, so the next run finds and reuses them.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimesheetReportSystemTest {

    private static final String PROJECT = System.getenv().getOrDefault("POLARION_SYSTEST_PROJECT", "elibrary");
    private static final String MARKER = "systest timesheet";
    private static final String START = "2030-03-04";
    private static final String END = "2030-03-08";
    private static final String OUTSIDE = "2030-02-28";
    /** Everything the fixture books sits in 2030, which is what tells its records from anybody else's. */
    private static final String FIXTURE_YEAR = "2030-";

    private final PolarionSystemTestSupport polarion = new PolarionSystemTestSupport();
    private final List<String> createdRecords = new ArrayList<>();
    private String firstUser;
    private String secondUser;
    private String workItem;
    private String otherWorkItem;

    private String v1(String path) {
        return "/polarion/rest/v1" + path;
    }

    private String timesheet(String path) {
        return "/polarion/timesheet/rest/api" + path;
    }

    /** Finds the work item carrying the marker, or creates it: Polarion's REST cannot delete one. */
    private String workItemFor(String title) {
        String query = PolarionSystemTestSupport.encode("title:\"%s\"".formatted(title));
        JsonNode found = polarion.json(v1("/projects/%s/workitems?query=%s&fields[workitems]=id,title&page[size]=10".formatted(PROJECT, query)));
        for (JsonNode candidate : found.path("data")) {
            if (title.equals(candidate.path("attributes").path("title").asText())) {
                return candidate.path("id").asText().substring(PROJECT.length() + 1);
            }
        }
        String body = """
                {"data":[{"type":"workitems","attributes":{"type":"task","title":"%s","status":"open"}}]}""".formatted(title);
        HttpResponse<String> response = polarion.post(v1("/projects/%s/workitems".formatted(PROJECT)), body);
        assertThat(response.statusCode()).as("creating %s", title).isIn(200, 201);
        String id = PolarionSystemTestSupport.parse(response.body()).path("data").get(0).path("id").asText();
        return id.substring(PROJECT.length() + 1);
    }

    private void addRecord(String workItemId, String user, String date, String timeSpent) {
        String body = """
                {"data":[{"type":"workrecords","attributes":{"date":"%s","timeSpent":"%s"},\
                "relationships":{"user":{"data":{"type":"users","id":"%s"}}}}]}""".formatted(date, timeSpent, user);
        HttpResponse<String> response = polarion.post(v1("/projects/%s/workitems/%s/workrecords".formatted(PROJECT, workItemId)), body);
        assertThat(response.statusCode()).as("adding a work record of %s on %s", user, date).isIn(200, 201);
        String id = PolarionSystemTestSupport.parse(response.body()).path("data").get(0).path("id").asText();
        createdRecords.add(id);
    }

    @BeforeAll
    void prepareTheData() {
        PolarionSystemTestSupport.assumeAPolarionIsRunning();
        // The REST API answers a token; the files of the webapp need a session.
        polarion.logIn();

        List<String> users = new ArrayList<>();
        for (JsonNode user : polarion.json(timesheet("/users"))) {
            users.add(user.path("id").asText());
        }
        assertThat(users).as("the instance needs two enabled users").hasSizeGreaterThanOrEqualTo(2);
        firstUser = users.getFirst();
        secondUser = users.get(1);

        workItem = workItemFor(MARKER + " one");
        otherWorkItem = workItemFor(MARKER + " two");

        // The work items outlive the run, so a run which was interrupted before removeTheRecords
        // left its records on them. Adding to those gives the next run a doubled report.
        clearFixtureRecords(workItem);
        clearFixtureRecords(otherWorkItem);

        // Two users, two work items, one day outside the period and one record of a third shape.
        addRecord(workItem, firstUser, START, "2h");
        // Polarion parses a duration as "3d 1/2h": halves are written as a fraction, not as minutes.
        addRecord(workItem, firstUser, "2030-03-05", "3 1/2h");
        addRecord(otherWorkItem, secondUser, END, "1h");
        addRecord(workItem, firstUser, OUTSIDE, "8h");
    }

    /**
     * Removes the records of the fixture from a work item, and only those: a booking of somebody
     * else on the same item is none of this test's business.
     */
    private void clearFixtureRecords(String workItemId) {
        JsonNode records = polarion.json(v1("/projects/%s/workitems/%s/workrecords?fields[workrecords]=date,user&page[size]=100"
                .formatted(PROJECT, workItemId)));
        for (JsonNode record : records.path("data")) {
            String date = record.path("attributes").path("date").asText();
            String user = record.path("relationships").path("user").path("data").path("id").asText();
            // The fixture year and one of the two users it books for. A booking of anybody else, on
            // the same work item and in the same year, is none of this test's business.
            if (date.startsWith(FIXTURE_YEAR) && (user.equals(firstUser) || user.equals(secondUser))) {
                deleteRecord(record.path("id").asText());
            }
        }
    }

    private void deleteRecord(String recordId) {
        String[] parts = recordId.split("/");
        polarion.delete(v1("/projects/%s/workitems/%s/workrecords/%s".formatted(parts[0], parts[1], parts[2])));
    }

    @AfterAll
    void removeTheRecords() {
        for (String record : createdRecords) {
            deleteRecord(record);
        }
    }

    private JsonNode report(String userIds, String startDate, String endDate) {
        return polarion.json(timesheet("/timesheet?user_ids=%s&start_date=%s&end_date=%s&scope_path=%s"
                .formatted(PolarionSystemTestSupport.encode(userIds), startDate, endDate, PROJECT)));
    }

    private static List<String> keysOf(JsonNode timesheet) {
        List<String> keys = new ArrayList<>();
        for (JsonNode record : timesheet.path("workRecords")) {
            keys.add("%s/%s/%s/%s".formatted(record.path("date").asText(), record.path("user").path("id").asText(),
                    record.path("workItem").path("id").asText(), record.path("hours").asText()));
        }
        return keys;
    }

    @Test
    void answersWithTheRecordsOfThePeriod() {
        JsonNode report = report(firstUser + "," + secondUser, START, END);

        assertThat(report.path("startDate").asText()).isEqualTo(START);
        assertThat(report.path("finishDate").asText()).isEqualTo(END);
        assertThat(keysOf(report)).containsExactlyInAnyOrder(
                "%s/%s/%s/2.0".formatted(START, firstUser, workItem),
                "2030-03-05/%s/%s/3.5".formatted(firstUser, workItem),
                "%s/%s/%s/1.0".formatted(END, secondUser, otherWorkItem));
    }

    /** The query finds work items with a record in the period, and such an item carries other records too. */
    @Test
    void leavesOutARecordOutsideThePeriod() {
        assertThat(keysOf(report(firstUser + "," + secondUser, START, END)))
                .noneMatch(key -> key.startsWith(OUTSIDE));

        assertThat(keysOf(report(firstUser, OUTSIDE, OUTSIDE)))
                .containsExactly("%s/%s/%s/8.0".formatted(OUTSIDE, firstUser, workItem));
    }

    @Test
    void leavesOutARecordOfAnotherUser() {
        assertThat(keysOf(report(firstUser, START, END)))
                .allMatch(key -> key.contains("/" + firstUser + "/"))
                .hasSize(2);
    }

    @Test
    void carriesTheRenderingPolarionMakesOfAWorkItem() {
        JsonNode first = report(firstUser, START, START).path("workRecords").get(0).path("workItem");

        assertThat(first.path("project").path("id").asText()).isEqualTo(PROJECT);
        assertThat(first.path("title").asText()).isEqualTo(MARKER + " one");
        // Polarion's own rendering of the work item, which the report shows and the PDF reuses.
        assertThat(first.path("html").asText()).contains(workItem).contains("<img");
        assertThat(first.path("iconUrl").asText()).startsWith("/polarion/");
    }

    @Test
    void listsTheEnabledUsersByName() {
        List<String> names = new ArrayList<>();
        for (JsonNode user : polarion.json(timesheet("/users"))) {
            names.add(user.path("name").asText());
        }

        assertThat(names).isNotEmpty().isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    void offersTheRepositoryAndItsProjectsAsScopes() {
        JsonNode scopes = polarion.json(timesheet("/scopes"));

        assertThat(scopes.get(0).path("path").asText()).isEqualTo("/");
        assertThat(scopes.get(0).path("type").asText()).isEqualTo("root");
        List<String> paths = new ArrayList<>();
        scopes.forEach(scope -> paths.add(scope.path("path").asText()));
        assertThat(paths).contains(PROJECT).doesNotHaveDuplicates();
    }

    /** A token carries no session user, so the endpoint answers 204 and the report preselects nobody. */
    @Test
    void answersNoCurrentUserForATokenCall() {
        assertThat(polarion.get(timesheet("/current-user")).statusCode()).isIn(200, 204);
    }

    @Test
    void servesTheWidgetIconAndTheAboutPage() {
        Map<String, String> expected = Map.of(
                // The URL TimesheetReportWidget.getIcon returns.
                "/polarion/timesheet-app/ui/images/widget-icon.svg", "<svg",
                "/polarion/timesheet-app/ui/html/about.html", "Timesheet Reports for Polarion ALM");

        expected.forEach((path, content) -> {
            HttpResponse<String> response = polarion.getAsUser(path);
            assertThat(response.statusCode()).as("GET %s", path).isEqualTo(200);
            assertThat(response.body()).as("body of %s", path).contains(content);
        });
        // README.md is converted into that page, and its badges must not travel with it.
        assertThat(polarion.getAsUser("/polarion/timesheet-app/ui/html/about.html").body())
                .doesNotContain("img.shields.io").doesNotContain("badge.svg").doesNotContain("<!DOCTYPE");
    }
}
