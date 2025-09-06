import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UrlShortenerApp {

    // Store Short URLs in memory
    private static Map<String, UrlData> urlStore = new HashMap<>();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Endpoints
        server.createContext("/shorten", new ShortenHandler());
        server.createContext("/redirect", new RedirectHandler());
        server.createContext("/stats", new StatsHandler());

        server.setExecutor(null); // default executor
        System.out.println("Server started at http://localhost:8080");
        server.start();
    }

    // === HANDLER TO CREATE SHORT URL ===
    static class ShortenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String request = new String(exchange.getRequestBody().readAllBytes());
                Map<String, String> params = parseQuery(request);

                String longUrl = params.getOrDefault("url", "");
                int validity = Integer.parseInt(params.getOrDefault("validity", "30"));
                String shortcode = UUID.randomUUID().toString().substring(0, 6);

                LocalDateTime expiry = LocalDateTime.now().plusMinutes(validity);
                urlStore.put(shortcode, new UrlData(longUrl, shortcode, expiry));

                // <-- Updated base URL
                String response = "Short URL: http://short.yerva/redirect?code=" + shortcode +
                        "\nExpiry: " + expiry;
                sendResponse(exchange, response, 200);
            } else {
                sendResponse(exchange, "Only POST allowed", 405);
            }
        }
    }

    // === HANDLER TO REDIRECT TO ORIGINAL URL ===
    static class RedirectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI requestURI = exchange.getRequestURI();
            Map<String, String> params = parseQuery(requestURI.getQuery());
            String code = params.get("code");

            if (code == null || !urlStore.containsKey(code)) {
                sendResponse(exchange, "Invalid or expired shortcode", 404);
                return;
            }

            UrlData data = urlStore.get(code);
            if (LocalDateTime.now().isAfter(data.expiry)) {
                sendResponse(exchange, "Link expired", 410);
                return;
            }

            data.incrementClick();
            sendResponse(exchange, "Redirect to: " + data.originalUrl, 200);
        }
    }

    // === HANDLER TO SHOW STATS ===
    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI requestURI = exchange.getRequestURI();
            Map<String, String> params = parseQuery(requestURI.getQuery());
            String code = params.get("code");

            if (code == null || !urlStore.containsKey(code)) {
                sendResponse(exchange, "Shortcode not found", 404);
                return;
            }

            UrlData data = urlStore.get(code);
            String response = "Original URL: " + data.originalUrl +
                    "\nExpiry: " + data.expiry +
                    "\nClick Count: " + data.clickCount;
            sendResponse(exchange, response, 200);
        }
    }

    // === UTIL: Send HTTP Response ===
    private static void sendResponse(HttpExchange exchange, String response, int status) throws IOException {
        exchange.sendResponseHeaders(status, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    // === UTIL: Parse Query Parameters ===
    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isEmpty()) return result;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2) result.put(pair[0], pair[1]);
        }
        return result;
    }

    // === URL DATA CLASS ===
    static class UrlData {
        String originalUrl;
        String shortcode;
        LocalDateTime expiry;
        int clickCount = 0;

        UrlData(String originalUrl, String shortcode, LocalDateTime expiry) {
            this.originalUrl = originalUrl;
            this.shortcode = shortcode;
            this.expiry = expiry;
        }

        void incrementClick() {
            clickCount++;
        }
    }
}
