package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static MoviesStore store;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    // ==================== Вспомогательные методы ====================

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String body, String contentType) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> delete(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .DELETE()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String addMovieAndGetId(String title, int year) throws Exception {
        String json = "{\"title\":\"" + title + "\",\"year\":" + year + "}";
        HttpResponse<String> resp = post("/movies", json, "application/json");
        assertEquals(201, resp.statusCode());
        String body = resp.body();
        int idStart = body.indexOf("\"id\":") + 5;
        int idEnd = body.indexOf(",", idStart);
        return body.substring(idStart, idEnd).trim();
    }

    // ==================== GET /movies ====================

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = get("/movies");

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertEquals("application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse(""),
                "Content-Type должен содержать формат данных и кодировку");
        assertEquals("[]", resp.body().trim(), "Ожидается пустой JSON-массив");
    }

    @Test
    void getMovies_whenNotEmpty_returnsAllMovies() throws Exception {
        addMovieAndGetId("Inception", 2010);
        addMovieAndGetId("Interstellar", 2014);

        HttpResponse<String> resp = get("/movies");

        assertEquals(200, resp.statusCode());
        assertEquals("application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse(""));
        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"));
        assertTrue(body.contains("Inception"));
        assertTrue(body.contains("Interstellar"));
    }

    // ==================== POST /movies ====================

    @Test
    void postMovies_whenValid_returnsCreatedMovie() throws Exception {
        String json = "{\"title\":\"Inception\",\"year\":2010}";
        HttpResponse<String> resp = post("/movies", json, "application/json");

        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201");
        assertEquals("application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse(""));
        String body = resp.body();
        assertTrue(body.contains("\"id\""), "Ответ должен содержать присвоенный ID");
        assertTrue(body.contains("Inception"));
        assertTrue(body.contains("2010"));
    }

    @Test
    void postMovies_whenEmptyTitle_returns422() throws Exception {
        String json = "{\"title\":\"\",\"year\":2010}";
        HttpResponse<String> resp = post("/movies", json, "application/json");

        assertEquals(422, resp.statusCode());
        String body = resp.body();
        assertTrue(body.contains("error"));
        assertTrue(body.contains("Ошибка валидации"));
        assertTrue(body.contains("название не должно быть пустым"));
    }

    @Test
    void postMovies_whenTitleIsNull_returns422() throws Exception {
        String json = "{\"year\":2010}";
        HttpResponse<String> resp = post("/movies", json, "application/json");

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("название не должно быть пустым"));
    }

    @Test
    void postMovies_whenTitleTooLong_returns422() throws Exception {
        String longTitle = "a".repeat(101);
        String json = "{\"title\":\"" + longTitle + "\",\"year\":2010}";
        HttpResponse<String> resp = post("/movies", json, "application/json");

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("Ошибка валидации"));
        assertTrue(resp.body().contains("название не должно превышать 100 символов"));
    }

    @Test
    void postMovies_whenTitleExactly100_returns201() throws Exception {
        String title = "a".repeat(100);
        String json = "{\"title\":\"" + title + "\",\"year\":2010}";
        HttpResponse<String> resp = post("/movies", json, "application/json");

        assertEquals(201, resp.statusCode());
    }

    @Test
    void postMovies_whenYearTooSmall_returns422() throws Exception {
        String json = "{\"title\":\"Old\",\"year\":1887}";
        HttpResponse<String> resp = post("/movies", json, "application/json");

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("год должен быть между 1888"));
    }

    @Test
    void postMovies_whenYear1888_returns201() throws Exception {
        String json = "{\"title\":\"Earliest\",\"year\":1888}";
        HttpResponse<String> resp = post("/movies", json, "application/json");

        assertEquals(201, resp.statusCode());
    }

    @Test
    void postMovies_whenYearTooBig_returns422() throws Exception {
        String json = "{\"title\":\"Future\",\"year\":3000}";
        HttpResponse<String> resp = post("/movies", json, "application/json");

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("год должен быть между"));
    }

    @Test
    void postMovies_whenWrongContentType_returns415() throws Exception {
        String json = "{\"title\":\"Inception\",\"year\":2010}";
        HttpResponse<String> resp = post("/movies", json, "text/plain");

        assertEquals(415, resp.statusCode());
        assertEquals("application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void postMovies_whenNoContentType_returns415() throws Exception {
        String json = "{\"title\":\"Inception\",\"year\":2010}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode());
    }

    @Test
    void postMovies_whenInvalidJson_returns422() throws Exception {
        String json = "{invalid json}";
        HttpResponse<String> resp = post("/movies", json, "application/json");

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("Ошибка валидации"));
    }

    @Test
    void postMovies_whenEmptyBody_returns422() throws Exception {
        HttpResponse<String> resp = post("/movies", "", "application/json");

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("Ошибка валидации"));
    }

    // ==================== GET /movies/{id} ====================

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        String id = addMovieAndGetId("Inception", 2010);

        HttpResponse<String> resp = get("/movies/" + id);

        assertEquals(200, resp.statusCode());
        assertEquals("application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse(""));
        assertTrue(resp.body().contains("Inception"));
        assertTrue(resp.body().contains("2010"));
        assertTrue(resp.body().contains("\"id\":" + id));
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = get("/movies/999");

        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("error"));
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void getMovieById_whenNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = get("/movies/abc");

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("error"));
        assertTrue(resp.body().contains("Некорректный ID"));
    }

    // ==================== DELETE /movies/{id} ====================

    @Test
    void deleteMovie_whenExists_returns204() throws Exception {
        String id = addMovieAndGetId("Inception", 2010);

        HttpResponse<String> resp = delete("/movies/" + id);

        assertEquals(204, resp.statusCode());

        // Проверяем, что фильм действительно удалён
        HttpResponse<String> getResp = get("/movies/" + id);
        assertEquals(404, getResp.statusCode());
    }

    @Test
    void deleteMovie_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = delete("/movies/999");

        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void deleteMovie_whenNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = delete("/movies/abc");

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный ID"));
    }

    // ==================== GET /movies?year=YYYY ====================

    @Test
    void getMoviesByYear_whenMatches_returnsFiltered() throws Exception {
        addMovieAndGetId("Inception", 2010);
        addMovieAndGetId("Interstellar", 2014);
        addMovieAndGetId("The Dark Knight", 2010);

        HttpResponse<String> resp = get("/movies?year=2010");

        assertEquals(200, resp.statusCode());
        assertEquals("application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse(""));
        String body = resp.body();
        assertTrue(body.contains("Inception"));
        assertTrue(body.contains("The Dark Knight"));
        assertFalse(body.contains("Interstellar"));
    }

    @Test
    void getMoviesByYear_whenNoMatches_returnsEmptyArray() throws Exception {
        addMovieAndGetId("Inception", 2010);

        HttpResponse<String> resp = get("/movies?year=1900");

        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    @Test
    void getMoviesByYear_whenNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = get("/movies?year=abc");

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("error"));
        assertTrue(resp.body().contains("Некорректный параметр запроса"));
    }

    @Test
    void getMoviesByYear_whenEmpty_returns400() throws Exception {
        HttpResponse<String> resp = get("/movies?year=");

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный параметр запроса"));
    }
}