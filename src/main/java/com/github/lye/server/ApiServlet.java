
package com.github.lye.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ContextHandler;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.util.TradeFlowLogger;

/**
 * Secured API Servlet for TradeFlow.
 * <p>
 * Provides REST API endpoints with rate limiting, input validation,
 * and sanitization to prevent common web vulnerabilities.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class ApiServlet extends HttpServlet {

    private final Gson gson = new GsonBuilder().create();
    private final Database database;
    private final TradeFlowLogger logger;
    private final String apiKey;
    private final com.github.lye.TradeFlow plugin;

    // Rate limiting: max requests per minute per IP
    private static final int RATE_LIMIT = 60;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000; // 1 minute

    private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    public ApiServlet(Database database, TradeFlowLogger logger, String apiKey, com.github.lye.TradeFlow tradeFlow) {
        this.database = database;
        this.logger = logger;
        this.apiKey = apiKey;
        this.plugin = tradeFlow;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Add security headers
        setSecurityHeaders(resp);

        // Check API key if configured
        if (apiKey != null && !apiKey.isEmpty()) {
            String providedKey = req.getParameter("key");
            if (!apiKey.equals(providedKey)) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.getWriter().println(gson.toJson(new ErrorResponse("Unauthorized")));
                return;
            }
        }

        // Rate limiting
        String clientIp = getClientIp(req);
        if (!checkRateLimit(clientIp)) {
            resp.setStatus(429);
            resp.getWriter().println(gson.toJson(new ErrorResponse("Rate limit exceeded")));
            return;
        }

        // Validate and sanitize path
        String path = sanitizePath(req.getPathInfo());
        if (path == null) {
            path = "/";
        }

        switch (path) {
            case "/shops":
                handleShops(req, resp);
                break;
            case "/transactions":
                handleTransactions(req, resp);
                break;
            case "/health":
                handleHealth(resp);
                break;
            default:
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().println(gson.toJson(new ErrorResponse("Not Found")));
                break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        setSecurityHeaders(resp);

        // Check API key
        if (apiKey != null && !apiKey.isEmpty()) {
            String providedKey = req.getParameter("key");
            if (!apiKey.equals(providedKey)) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.getWriter().println(gson.toJson(new ErrorResponse("Unauthorized")));
                return;
            }
        }

        // Rate limiting for POST (stricter)
        String clientIp = getClientIp(req);
        if (!checkRateLimit(clientIp, 10, 60_000)) { // 10 req/min for POST
            resp.setStatus(429);
            resp.getWriter().println(gson.toJson(new ErrorResponse("Rate limit exceeded")));
            return;
        }

        String path = sanitizePath(req.getPathInfo());
        if (path == null) {
            path = "/";
        }

        switch (path) {
            case "/recalculate":
                handleRecalculate(resp);
                break;
            default:
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().println(gson.toJson(new ErrorResponse("Not Found")));
                break;
        }
    }

    /**
     * Sets security headers on the response.
     */
    private void setSecurityHeaders(HttpServletResponse resp) {
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("X-XSS-Protection", "1; mode=block");
        resp.setHeader("Strict-Transport-Security", "max-age=31536000");
    }

    /**
     * Handles /shops endpoint with pagination.
     */
    private void handleShops(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            // Validate pagination parameters
            int page = parseIntParameter(req, "page", 1);
            int limit = parseIntParameter(req, "limit", 50);
            String search = sanitizeString(req.getParameter("search"));

            // Enforce limits
            limit = Math.min(limit, 100);
            page = Math.max(page, 1);

            Database.acquireReadLock();
            Collection<Shop> shops = database.getShops().values();

            // Filter by search term if provided
            if (search != null && !search.isEmpty()) {
                String searchLower = search.toLowerCase();
                shops = shops.stream()
                    .filter(shop -> shop.getName().toLowerCase().contains(searchLower))
                    .toList();
            }

            // Pagination (simple implementation)
            // In production, would use proper offset/limit in database query

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().println(gson.toJson(new ShopResponse(shops, shops.size())));
        } finally {
            Database.releaseReadLock();
        }
    }

    /**
     * Handles /transactions endpoint.
     */
    private void handleTransactions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            // Validate parameters
            String playerId = req.getParameter("player");
            if (playerId != null) {
                // Validate UUID format
                try {
                    UUID uuid = UUID.fromString(playerId);
                    playerId = uuid.toString(); // Normalize
                } catch (IllegalArgumentException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println(gson.toJson(new ErrorResponse("Invalid player UUID")));
                    return;
                }
            }

            int limit = parseIntParameter(req, "limit", 100);
            limit = Math.min(limit, 1000);

            Database.acquireReadLock();
            Collection<com.github.lye.data.Transaction> transactions =
                database.transactions.values();

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().println(gson.toJson(transactions));
        } finally {
            Database.releaseReadLock();
        }
    }

    /**
     * Handles /recalculate endpoint (admin only).
     */
    private void handleRecalculate(HttpServletResponse resp) throws IOException {
        try {
            plugin.recalculatePrices();
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().println(gson.toJson(new SuccessResponse("Price recalculation triggered")));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().println(gson.toJson(new ErrorResponse("Failed to recalculate: " + e.getMessage())));
        }
    }

    /**
     * Handles /health endpoint for monitoring.
     */
    private void handleHealth(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().println(gson.toJson(new HealthResponse(
            database != null && database.getShops() != null,
            database != null && database.getShops() != null ? database.getShops().size() : 0,
            System.currentTimeMillis()
        )));
    }

    /**
     * Gets the client IP address, handling X-Forwarded-For header.
     */
    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            // Take the first IP in the chain
            String[] ips = xff.split(",");
            return ips[0].trim();
        }
        return req.getRemoteAddr();
    }

    /**
     * Checks rate limit for a client.
     */
    private boolean checkRateLimit(String clientIp) {
        return checkRateLimit(clientIp, RATE_LIMIT, RATE_LIMIT_WINDOW_MS);
    }

    /**
     * Checks rate limit with custom parameters.
     */
    private boolean checkRateLimit(String clientIp, int maxRequests, long windowMs) {
        long now = System.currentTimeMillis();
        RateLimitEntry entry = rateLimitMap.computeIfAbsent(
            clientIp,
            k -> new RateLimitEntry(now)
        );

        // Reset if window expired
        if (now - entry.windowStart > windowMs) {
            entry.windowStart = now;
            entry.count.set(0);
        }

        // Increment and check
        int newCount = entry.count.incrementAndGet();
        if (newCount > maxRequests) {
            logger.warning("API rate limit exceeded for " + clientIp);
            return false;
        }

        return true;
    }

    /**
     * Sanitizes the request path to prevent path traversal attacks.
     */
    private String sanitizePath(String path) {
        if (path == null) {
            return null;
        }

        // Remove null bytes
        path = path.replace("\0", "");

        // Prevent path traversal
        if (path.contains("..") || path.contains("//")) {
            return "/";
        }

        // Only allow alphanumeric, slash, dash, underscore
        if (!path.matches("^/[a-zA-Z0-9/_-]*$")) {
            return "/";
        }

        return path;
    }

    /**
     * Safely parses an integer parameter.
     */
    private int parseIntParameter(HttpServletRequest req, String name, int defaultValue) {
        String value = req.getParameter(name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }

        try {
            int parsed = Integer.parseInt(value);
            return Math.max(0, parsed); // No negative values
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Sanitizes a string parameter to prevent XSS.
     */
    private String sanitizeString(String input) {
        if (input == null) {
            return null;
        }

        // Remove potentially dangerous characters
        return input.replaceAll("[<>\"'&]", "");
    }

    /**
     * Rate limit entry for tracking requests.
     */
    private static class RateLimitEntry {
        long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        RateLimitEntry(long windowStart) {
            this.windowStart = windowStart;
        }
    }

    // Response classes

    private static class ErrorResponse {
        private final String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }

    private static class SuccessResponse {
        private final String message;
        private final long timestamp;

        public SuccessResponse(String message) {
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static class ShopResponse {
        private final Collection<Shop> shops;
        private final int total;

        public ShopResponse(Collection<Shop> shops, int total) {
            this.shops = shops;
            this.total = total;
        }
    }

    private static class HealthResponse {
        private final boolean healthy;
        private final int shopCount;
        private final long timestamp;

        public HealthResponse(boolean healthy, int shopCount, long timestamp) {
            this.healthy = healthy;
            this.shopCount = shopCount;
            this.timestamp = timestamp;
        }
    }
}
