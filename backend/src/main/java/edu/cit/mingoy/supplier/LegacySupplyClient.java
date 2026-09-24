package edu.cit.mingoy.supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class LegacySupplyClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 250;

    private static final Pattern SESSION_TOKEN_PATTERN =
            Pattern.compile("<SessionToken>([^<]+)</SessionToken>");

    private static final Pattern PO_NUMBER_PATTERN =
            Pattern.compile("<PoNumber>([^<]+)</PoNumber>");

    private static final Pattern STATUS_CODE_PATTERN =
            Pattern.compile("<StatusCode>([^<]+)</StatusCode>");

    private static final Pattern QTY_PATTERN =
            Pattern.compile("<Qty>([^<]+)</Qty>");

    private static final Pattern UOM_PATTERN =
            Pattern.compile("<Uom>([^<]+)</Uom>");

    private static final Pattern ERROR_CODE_PATTERN =
            Pattern.compile("<Code>([^<]+)</Code>");

    private static final Pattern ERROR_MESSAGE_PATTERN =
            Pattern.compile("<Message>([^<]+)</Message>");

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String clientId;
    private final String apiKey;

    private String sessionToken;

    LegacySupplyClient(
            @Value("${LS_BASE_URL:https://legacysupply.onrender.com/api/v1}") String baseUrl,
            @Value("${LS_CLIENT_ID}") String clientId,
            @Value("${LS_API_KEY}") String apiKey
    ) {
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    synchronized LegacyOrderResponse placePurchaseOrder(
            String supplierSku,
            int cases,
            String buyerRef,
            String requestId
    ) {
        LegacyOrderResponse lastResponse = null;
        boolean sessionRenewed = false;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (sessionToken == null) {
                try {
                    signIn();
                } catch (RuntimeException exception) {
                    lastResponse = LegacyOrderResponse.failure(
                            0,
                            "AUTH_ERROR",
                            exception.getMessage()
                    );
                }
            }

            if (lastResponse != null &&
                    "AUTH_ERROR".equals(lastResponse.errorCode())) {

                if (attempt == MAX_ATTEMPTS) {
                    return lastResponse;
                }

                sleepBeforeRetry(attempt);
                continue;
            }

            LegacyOrderResponse response = sendPurchaseOrder(
                    supplierSku,
                    cases,
                    buyerRef,
                    requestId
            );

            if (response.success()) {
                return response;
            }

            lastResponse = response;

            if (isExpiredSession(response.errorCode()) &&
                    !sessionRenewed &&
                    attempt < MAX_ATTEMPTS) {

                sessionRenewed = true;

                try {
                    signIn();
                } catch (RuntimeException exception) {
                    lastResponse = LegacyOrderResponse.failure(
                            0,
                            "AUTH_ERROR",
                            exception.getMessage()
                    );

                    sleepBeforeRetry(attempt);
                }

                continue;
            }

            if (!isRetryable(response) ||
                    attempt == MAX_ATTEMPTS) {
                return response;
            }

            sleepBeforeRetry(attempt);
        }

        return lastResponse;
    }

    synchronized LegacyTrackingResponse getPurchaseOrder(
            String poNumber
    ) {
        if (poNumber == null || poNumber.isBlank()) {
            return LegacyTrackingResponse.failure(
                    0,
                    "INVALID_PO",
                    "Purchase order number is required."
            );
        }

        LegacyTrackingResponse lastResponse = null;
        boolean sessionRenewed = false;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (sessionToken == null) {
                try {
                    signIn();
                } catch (RuntimeException exception) {
                    lastResponse = LegacyTrackingResponse.failure(
                            0,
                            "AUTH_ERROR",
                            exception.getMessage()
                    );
                }
            }

            if (lastResponse != null &&
                    "AUTH_ERROR".equals(lastResponse.errorCode())) {

                if (attempt == MAX_ATTEMPTS) {
                    return lastResponse;
                }

                sleepBeforeRetry(attempt);
                continue;
            }

            LegacyTrackingResponse response =
                    sendPurchaseOrderTracking(poNumber);

            if (response.success()) {
                return response;
            }

            lastResponse = response;

            if (isExpiredSession(response.errorCode()) &&
                    !sessionRenewed &&
                    attempt < MAX_ATTEMPTS) {

                sessionRenewed = true;

                try {
                    signIn();
                } catch (RuntimeException exception) {
                    lastResponse = LegacyTrackingResponse.failure(
                            0,
                            "AUTH_ERROR",
                            exception.getMessage()
                    );

                    sleepBeforeRetry(attempt);
                }

                continue;
            }

            if (!isRetryable(response) ||
                    attempt == MAX_ATTEMPTS) {
                return response;
            }

            sleepBeforeRetry(attempt);
        }

        return lastResponse;
    }

    private LegacyOrderResponse sendPurchaseOrder(
            String supplierSku,
            int cases,
            String buyerRef,
            String requestId
    ) {
        String xml = """
                <PurchaseOrder>
                    <SupplierSku>%s</SupplierSku>
                    <Qty>%d</Qty>
                    <BuyerRef>%s</BuyerRef>
                </PurchaseOrder>
                """.formatted(
                escapeXml(supplierSku),
                cases,
                escapeXml(buyerRef)
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/purchase-orders"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/xml")
                    .header("X-LS-Session", sessionToken)
                    .header("X-Request-Id", requestId)
                    .POST(HttpRequest.BodyPublishers.ofString(xml))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            String body = response.body();

            if (response.statusCode() == 200 ||
                    response.statusCode() == 201) {
                return parseSuccessfulOrder(body);
            }

            String errorCode = extract(
                    ERROR_CODE_PATTERN,
                    body,
                    "UNKNOWN"
            );

            String errorMessage = extract(
                    ERROR_MESSAGE_PATTERN,
                    body,
                    "Unknown supplier error"
            );

            return LegacyOrderResponse.failure(
                    response.statusCode(),
                    errorCode,
                    errorMessage
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            return LegacyOrderResponse.failure(
                    0,
                    "INTERRUPTED",
                    "LegacySupply request was interrupted."
            );

        } catch (IOException exception) {
            return LegacyOrderResponse.failure(
                    0,
                    "IO_ERROR",
                    exception.getMessage() == null
                            ? "LegacySupply connection failed."
                            : exception.getMessage()
            );
        }
    }

    private LegacyTrackingResponse sendPurchaseOrderTracking(
            String poNumber
    ) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            baseUrl + "/purchase-orders/" +
                                    escapePath(poNumber)
                    ))
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "application/xml")
                    .header("X-LS-Session", sessionToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            String body = response.body();

            if (response.statusCode() == 200) {
                return parseTrackingResponse(body);
            }

            String errorCode = extract(
                    ERROR_CODE_PATTERN,
                    body,
                    "UNKNOWN"
            );

            String errorMessage = extract(
                    ERROR_MESSAGE_PATTERN,
                    body,
                    "Unknown supplier error"
            );

            return LegacyTrackingResponse.failure(
                    response.statusCode(),
                    errorCode,
                    errorMessage
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            return LegacyTrackingResponse.failure(
                    0,
                    "INTERRUPTED",
                    "LegacySupply tracking request was interrupted."
            );

        } catch (IOException exception) {
            return LegacyTrackingResponse.failure(
                    0,
                    "IO_ERROR",
                    exception.getMessage() == null
                            ? "LegacySupply connection failed."
                            : exception.getMessage()
            );
        }
    }

    synchronized void signIn() {
        String xml = """
                <AuthRequest>
                    <ClientId>%s</ClientId>
                    <ApiKey>%s</ApiKey>
                </AuthRequest>
                """.formatted(
                escapeXml(clientId),
                escapeXml(apiKey)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/token"))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofString(xml))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "LegacySupply authentication failed. HTTP " +
                                response.statusCode() +
                                ": " +
                                extract(
                                        ERROR_MESSAGE_PATTERN,
                                        response.body(),
                                        "Credentials rejected."
                                )
                );
            }

            Matcher matcher =
                    SESSION_TOKEN_PATTERN.matcher(response.body());

            if (!matcher.find()) {
                throw new IllegalStateException(
                        "LegacySupply authentication succeeded but no session token was returned."
                );
            }

            sessionToken = matcher.group(1);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "LegacySupply authentication was interrupted.",
                    exception
            );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not connect to LegacySupply.",
                    exception
            );
        }
    }

    private LegacyOrderResponse parseSuccessfulOrder(
            String body
    ) {
        String poNumber = extract(
                PO_NUMBER_PATTERN,
                body,
                null
        );

        String statusCode = extract(
                STATUS_CODE_PATTERN,
                body,
                null
        );

        String qty = extract(
                QTY_PATTERN,
                body,
                null
        );

        String uom = extract(
                UOM_PATTERN,
                body,
                null
        );

        return LegacyOrderResponse.success(
                poNumber,
                statusCode,
                qty,
                uom
        );
    }

    private LegacyTrackingResponse parseTrackingResponse(
            String body
    ) {
        String poNumber = extract(
                PO_NUMBER_PATTERN,
                body,
                null
        );

        String statusCode = extract(
                STATUS_CODE_PATTERN,
                body,
                null
        );

        String qty = extract(
                QTY_PATTERN,
                body,
                null
        );

        String uom = extract(
                UOM_PATTERN,
                body,
                null
        );

        return LegacyTrackingResponse.success(
                poNumber,
                statusCode,
                qty,
                uom
        );
    }

    private boolean isExpiredSession(
            String errorCode
    ) {
        return "E-AUTH-02".equals(errorCode)
                || "E-AUTH-03".equals(errorCode)
                || "E-AUTH-07".equals(errorCode);
    }

    private boolean isRetryable(
            LegacyOrderResponse response
    ) {
        return response.httpStatus() == 0
                || response.httpStatus() == 429
                || response.httpStatus() == 503
                || "IO_ERROR".equals(response.errorCode());
    }

    private boolean isRetryable(
            LegacyTrackingResponse response
    ) {
        return response.httpStatus() == 0
                || response.httpStatus() == 429
                || response.httpStatus() == 503
                || "IO_ERROR".equals(response.errorCode());
    }

    private void sleepBeforeRetry(
            int attempt
    ) {
        long delay =
                INITIAL_BACKOFF_MILLIS *
                        (1L << (attempt - 1));

        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static String extract(
            Pattern pattern,
            String text,
            String defaultValue
    ) {
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return defaultValue;
    }

    private static String escapeXml(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String escapePath(
            String value
    ) {
        return value
                .replace("%", "%25")
                .replace(" ", "%20")
                .replace("?", "%3F")
                .replace("#", "%23");
    }

    record LegacyOrderResponse(
            boolean success,
            int httpStatus,
            String poNumber,
            String supplierStatusCode,
            String qty,
            String uom,
            String errorCode,
            String errorMessage
    ) {

        static LegacyOrderResponse success(
                String poNumber,
                String supplierStatusCode,
                String qty,
                String uom
        ) {
            return new LegacyOrderResponse(
                    true,
                    201,
                    poNumber,
                    supplierStatusCode,
                    qty,
                    uom,
                    null,
                    null
            );
        }

        static LegacyOrderResponse failure(
                int httpStatus,
                String errorCode,
                String errorMessage
        ) {
            return new LegacyOrderResponse(
                    false,
                    httpStatus,
                    null,
                    null,
                    null,
                    null,
                    errorCode,
                    errorMessage
            );
        }
    }

    record LegacyTrackingResponse(
            boolean success,
            int httpStatus,
            String poNumber,
            String supplierStatusCode,
            String qty,
            String uom,
            String errorCode,
            String errorMessage
    ) {

        static LegacyTrackingResponse success(
                String poNumber,
                String supplierStatusCode,
                String qty,
                String uom
        ) {
            return new LegacyTrackingResponse(
                    true,
                    200,
                    poNumber,
                    supplierStatusCode,
                    qty,
                    uom,
                    null,
                    null
            );
        }

        static LegacyTrackingResponse failure(
                int httpStatus,
                String errorCode,
                String errorMessage
        ) {
            return new LegacyTrackingResponse(
                    false,
                    httpStatus,
                    null,
                    null,
                    null,
                    null,
                    errorCode,
                    errorMessage
            );
        }
    }
}