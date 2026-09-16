package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;
    private final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();

        try {
            if (path.equals("/movies") || path.equals("/movies/")) {
                switch (method.toUpperCase()) {
                    case "GET":
                        handleGetAll(ex, query);
                        break;
                    case "POST":
                        handlePost(ex);
                        break;
                    default:
                        sendJson(ex, 405, gson.toJson(
                                new ErrorResponse("Метод не поддерживается")));
                }
            } else if (path.startsWith("/movies/")) {
                String idPart = path.substring("/movies/".length());
                switch (method.toUpperCase()) {
                    case "GET":
                        handleGetById(ex, idPart);
                        break;
                    case "DELETE":
                        handleDelete(ex, idPart);
                        break;
                    default:
                        sendJson(ex, 405, gson.toJson(
                                new ErrorResponse("Метод не поддерживается")));
                }
            } else {
                sendJson(ex, 404, gson.toJson(
                        new ErrorResponse("Не найдено")));
            }
        } catch (Exception e) {
            sendJson(ex, 500, gson.toJson(
                    new ErrorResponse("Внутренняя ошибка сервера")));
        }
    }

    private void handleGetAll(HttpExchange ex, String query) throws IOException {
        if (query != null && query.contains("year=")) {
            String yearParam = null;
            for (String param : query.split("&")) {
                if (param.startsWith("year=")) {
                    yearParam = param.substring("year=".length());
                    break;
                }
            }
            if (yearParam == null || yearParam.isEmpty()) {
                sendJson(ex, 400, gson.toJson(new ErrorResponse(
                        "Некорректный параметр запроса — 'year'")));
                return;
            }
            try {
                int year = Integer.parseInt(yearParam);
                List<Movie> movies = store.getByYear(year);
                sendJson(ex, 200, gson.toJson(movies));
            } catch (NumberFormatException e) {
                sendJson(ex, 400, gson.toJson(new ErrorResponse(
                        "Некорректный параметр запроса — 'year'")));
            }
        } else {
            List<Movie> movies = store.getAll();
            sendJson(ex, 200, gson.toJson(movies));
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendJson(ex, 415, gson.toJson(new ErrorResponse("Unsupported Media Type")));
            return;
        }

        String body;
        try (InputStream is = ex.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        Movie incoming;
        try {
            incoming = gson.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            sendJson(ex, 422, gson.toJson(new ErrorResponse(
                    "Ошибка валидации", "Некорректный JSON")));
            return;
        }

        if (incoming == null) {
            sendJson(ex, 422, gson.toJson(new ErrorResponse(
                    "Ошибка валидации", "Некорректный JSON")));
            return;
        }

        List<String> details = new ArrayList<>();

        String title = incoming.getTitle();
        if (title == null || title.trim().isEmpty()) {
            details.add("название не должно быть пустым");
        } else if (title.length() > 100) {
            details.add("название не должно превышать 100 символов");
        }

        int currentYear = Year.now().getValue();
        int maxYear = currentYear + 1;
        int year = incoming.getYear();
        if (year < 1888 || year > maxYear) {
            details.add("год должен быть между 1888 и " + maxYear);
        }

        if (!details.isEmpty()) {
            sendJson(ex, 422, gson.toJson(new ErrorResponse(
                    "Ошибка валидации", String.join(", ", details))));
            return;
        }

        Movie movie = new Movie(0, title.trim(), year);
        Movie created = store.add(movie);
        sendJson(ex, 201, gson.toJson(created));
    }

    private void handleGetById(HttpExchange ex, String idPart) throws IOException {
        int id;
        try {
            id = Integer.parseInt(idPart);
        } catch (NumberFormatException e) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный ID")));
            return;
        }

        Optional<Movie> movie = store.getById(id);
        if (movie.isPresent()) {
            sendJson(ex, 200, gson.toJson(movie.get()));
        } else {
            sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
        }
    }

    private void handleDelete(HttpExchange ex, String idPart) throws IOException {
        int id;
        try {
            id = Integer.parseInt(idPart);
        } catch (NumberFormatException e) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный ID")));
            return;
        }

        if (store.delete(id)) {
            sendNoContent(ex);
        } else {
            sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
        }
    }
}