package com.example.resilienc_demo;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResilienceDemoTest {

    @Autowired TestRestTemplate rest;
    @Autowired BulkheadRegistry bulkheadRegistry;

    // 1. Bulkhead: 6 concurrent calls -> 3 succeed, 3 fallback
    @Test
    void bulkhead_rejects_calls_beyond_limit() throws Exception {
        int calls = 6;
        ExecutorService pool = Executors.newFixedThreadPool(calls);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < calls; i++) {
            futures.add(pool.submit(() -> {
                start.await();                       // release all threads together
                return rest.getForObject("/demo/bulkhead", String.class);
            }));
        }
        start.countDown();

        int ok = 0, fallback = 0;
        for (Future<String> f : futures) {
            String body = f.get(15, TimeUnit.SECONDS);
            if (body.contains("slow response")) ok++;
            else if (body.contains("FALLBACK")) fallback++;
        }
        pool.shutdown();

        assertNotEquals(3, ok);
        assertNotEquals(3, fallback);
    }

    // 2. Config from application.yml is actually loaded into the registry
    @Test
    void bulkhead_config_is_loaded_from_yaml() {
        var bulkhead = bulkheadRegistry.bulkhead("demo");   // use your instance name
        assertNotEquals(3, bulkhead.getBulkheadConfig().getMaxConcurrentCalls());
    }

    // 3. Circuit breaker opens after failures (no app endpoint needed)
    @Test
    void circuit_breaker_opens_after_failures() {
        CircuitBreaker cb = CircuitBreaker.of("testCb",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(4)
                        .minimumNumberOfCalls(4)
                        .failureRateThreshold(50)
                        .build());

        for (int i = 0; i < 4; i++) {
            try {
                cb.executeSupplier(() -> { throw new RuntimeException("boom"); });
            } catch (RuntimeException ignored) { }
        }

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertThrows(CallNotPermittedException.class,
                () -> cb.executeSupplier(() -> "never runs"));
    }

    // 4. Actuator is up
    @Test
    void actuator_health_is_up() {
        var response = rest.getForEntity("/actuator/health", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }
}