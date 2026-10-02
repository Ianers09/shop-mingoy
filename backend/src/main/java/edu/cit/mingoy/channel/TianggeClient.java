package edu.cit.mingoy.channel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
class TianggeClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final InstanceIdentity instanceIdentity;

    TianggeClient(
            @Value("${shop.tiangge.base-url}") String baseUrl,
            @Value("${shop.tiangge.client-id}") String clientId,
            @Value("${shop.tiangge.api-key}") String apiKey,
            InstanceIdentity instanceIdentity
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.instanceIdentity = instanceIdentity;
    }

    String heartbeat(String body) {
        return send("POST", "/instances/heartbeat", body);
    }

    String publishListings(String body) {
        return send("PUT", "/listings", body);
    }

    String publishStock(String body) {
        return send("PUT", "/stock", body);
    }

    String feed(long cursor, int limit) {
        return send("GET", "/feed?after=" + cursor + "&limit=" + limit, null);
    }

    String decide(String orderId, String body) {
        return send("POST", "/orders/" + encodePath(orderId) + "/decision", body);
    }

    String resolve(String orderId, String body) {
        return send("POST", "/orders/" + encodePath(orderId) + "/resolution", body);
    }

    String confirmCancellation(String orderId, String body) {
        return send("POST", "/orders/" + encodePath(orderId) + "/cancellation", body);
    }

    private String send(String method, String path, String body) {
        int maxAttempts = 4;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(20))
                        .header("X-Client-Id", clientId)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("X-Client-Instance", instanceIdentity.getInstanceId())
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json");

                if ("GET".equals(method)) {
                    builder.GET();
                } else if ("POST".equals(method)) {
                    builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
                } else {
                    builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
                }

                HttpResponse<String> response = httpClient.send(
                        builder.build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }

                if (response.statusCode() != 503 || attempt == maxAttempts) {
                    throw new IllegalStateException(
                            "Tiangge request failed: HTTP "
                                    + response.statusCode()
                                    + " "
                                    + response.body()
                    );
                }

                long delay = 1000L * (1L << (attempt - 1));

                System.out.println(
                        "TIANGGE_RETRY status=503 attempt="
                                + attempt
                                + " delayMs="
                                + delay
                                + " path="
                                + path
                );

                Thread.sleep(delay);

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Tiangge request interrupted",
                        exception
                );
            } catch (Exception exception) {
                if (exception instanceof IllegalStateException illegalStateException) {
                    throw illegalStateException;
                }

                throw new IllegalStateException(
                        "Tiangge request failed",
                        exception
                );
            }
        }

        throw new IllegalStateException("Tiangge request failed");
    }

    private String encodePath(String value) {
        return value
                .replace("%", "%25")
                .replace("/", "%2F")
                .replace(" ", "%20")
                .replace("?", "%3F")
                .replace("#", "%23");
    }
}