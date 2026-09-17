package com.intechcore.polarion.extension.timesheet.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Talks to a running Polarion. Everything the system tests need and nothing else: the two ways in,
 * and JSON over HTTP.
 * <p>
 * Polarion answers an extension's REST calls in two contexts. {@code /api} takes a personal access
 * token, and {@code /internal} takes a session. Generic's settings endpoints need the session: under
 * a token they answer 409, "Cannot find request attributes in the request context". The login form
 * carries a CSRF token, so signing in takes two requests.
 */
final class PolarionSystemTestSupport {

    static final String BASE_URL = System.getenv().getOrDefault("POLARION_URL", "http://localhost");
    static final String TOKEN = System.getenv("POLARION_TOKEN");
    static final String USER = System.getenv().getOrDefault("POLARION_USER", "admin");
    static final String PASSWORD = System.getenv().getOrDefault("POLARION_PASSWORD", "admin");

    private static final Pattern CSRF_TOKEN = Pattern.compile("id=\"csrfToken\"[^>]*value=\"([^\"]+)\"");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Skips the test when there is no Polarion to talk to, or no token to talk with. A system test
     * that cannot run is not a failing test: it is one that was not asked for.
     */
    static void assumeAPolarionIsRunning() {
        assumeTrue(TOKEN != null && !TOKEN.isBlank(), "POLARION_TOKEN is not set");
        assumeTrue(answers(), "No Polarion answers at " + BASE_URL);
    }

    private static boolean answers() {
        try {
            HttpResponse<Void> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + "/polarion/")).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Signs in with the login form, which is what the {@code /internal} endpoints answer to. */
    void logIn() {
        String loginPage = send(request("/polarion/").GET(), "/rest/internal").body();
        Matcher csrfToken = CSRF_TOKEN.matcher(loginPage);
        String form = "j_username=%s&j_password=%s&target=%s".formatted(encode(USER), encode(PASSWORD), encode("/polarion/"))
                + (csrfToken.find() ? "&csrfToken=" + encode(csrfToken.group(1)) : "");

        HttpResponse<String> response = send(request("/polarion/j_security_check")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)), "/rest/internal");
        if (response.statusCode() != 303 && response.statusCode() != 302 && response.statusCode() != 200) {
            throw new IllegalStateException("Could not sign in as " + USER + ": HTTP " + response.statusCode());
        }
    }

    HttpResponse<String> get(@NotNull String path) {
        return send(request(path).GET(), path);
    }

    /**
     * Fetches as the signed-in user, without the token. The files a {@code GenericUiServlet} serves
     * need a Polarion session: a request carrying only a token is answered with the login page.
     */
    HttpResponse<String> getAsUser(@NotNull String path) {
        return send(request(path).GET(), "/rest/internal");
    }

    HttpResponse<String> put(@NotNull String path, @NotNull String body) {
        return send(request(path).header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body)), path);
    }

    HttpResponse<String> post(@NotNull String path, @NotNull String body) {
        return send(request(path).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)), path);
    }

    HttpResponse<String> delete(@NotNull String path) {
        return send(request(path).DELETE(), path);
    }

    @NotNull JsonNode json(@NotNull String path) {
        HttpResponse<String> response = get(path);
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GET %s answered %d: %s".formatted(path, response.statusCode(), response.body()));
        }
        return parse(response.body());
    }

    static @NotNull JsonNode parse(@Nullable String body) {
        try {
            return JSON.readTree(body == null || body.isBlank() ? "null" : body);
        } catch (IOException e) {
            throw new IllegalStateException("Not JSON: " + body, e);
        }
    }

    static @NotNull String encode(@NotNull String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private HttpRequest.Builder request(@NotNull String path) {
        return HttpRequest.newBuilder(URI.create(BASE_URL + path)).timeout(Duration.ofMinutes(2));
    }

    private HttpResponse<String> send(@NotNull HttpRequest.Builder builder, @NotNull String path) {
        // The token reaches /api and the REST v1 endpoints. An /internal call must go with the session
        // cookie alone: sent together, the token wins and the call is refused.
        if (!path.contains("/rest/internal") && TOKEN != null && !TOKEN.isBlank()) {
            builder = builder.header("Authorization", "Bearer " + TOKEN);
        }
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Request to " + BASE_URL + " failed", e);
        }
    }
}
