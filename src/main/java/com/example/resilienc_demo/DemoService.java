package com.example.resilienc_demo;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

/**
 * Each method uses ONE pattern. The settings live in application.yml.
 * The controller must call these through the Spring proxy (a different class),
 * otherwise the annotations do nothing.
 */
@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    private final RestClient client;

    public DemoService(@Value("${server.port:8080}") int port) {
        this.client = RestClient.create("http://localhost:" + port);
    }

    private String get(String path) {
        return client.get().uri(path).retrieve().body(String.class);
    }

    // 1. RETRY ---------------------------------------------------------------
    @Retry(name = "demoRetry", fallbackMethod = "retryFallback")
    public String retryDemo() {
        log.info("RETRY demo: attempt calling /downstream/flaky");
        return get("/downstream/flaky");
    }

    public String retryFallback(Throwable t) {
        return "FALLBACK: all retry attempts failed (" + t.getClass().getSimpleName() + ")";
    }

    // 2. CIRCUIT BREAKER -----------------------------------------------------
    @CircuitBreaker(name = "demoBreaker", fallbackMethod = "breakerFallback")
    public String breakerDemo() {
        log.info("BREAKER demo: really calling /downstream/fail");
        return get("/downstream/fail");
    }

    public String breakerFallback(Throwable t) {
        // CallNotPermittedException means the breaker is OPEN and rejected the call instantly
        return "FALLBACK: " + t.getClass().getSimpleName();
    }

    // 3. TIME LIMITER (needs CompletableFuture) -----------------------------
    @TimeLimiter(name = "demoTimeLimiter", fallbackMethod = "timeoutFallback")
    public CompletableFuture<String> timeLimiterDemo() {
        return CompletableFuture.supplyAsync(() -> {
            log.info("TIME LIMITER demo: calling /downstream/slow?ms=5000");
            return get("/downstream/slow?ms=5000");
        });
    }

    public CompletableFuture<String> timeoutFallback(Throwable t) {
        return CompletableFuture.completedFuture("FALLBACK: gave up waiting (" + t.getClass().getSimpleName() + ")");
    }

    // 4. BULKHEAD ------------------------------------------------------------
    @Bulkhead(name = "demoBulkhead", fallbackMethod = "bulkheadFallback")
    public String bulkheadDemo() {
        log.info("BULKHEAD demo: inside the bulkhead, calling /downstream/slow?ms=3000");
        return get("/downstream/slow?ms=3000");
    }

    public String bulkheadFallback(Throwable t) {
        return "FALLBACK: rejected (" + t.getClass().getSimpleName() + ")";
    }

    // 5. RATE LIMITER --------------------------------------------------------
    @RateLimiter(name = "demoLimiter", fallbackMethod = "rateFallback")
    public String rateLimiterDemo() {
        return get("/downstream/ok");
    }

    public String rateFallback(Throwable t) {
        return "FALLBACK: too many requests (" + t.getClass().getSimpleName() + ")";
    }
}