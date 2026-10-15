package com.example.resilienc_demo;

import java.util.concurrent.CompletableFuture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoController {

    private final DemoService service;

    public DemoController(DemoService service) {
        this.service = service;
    }

    @GetMapping("/retry")
    public String retry() {
        return service.retryDemo();
    }

    @GetMapping("/circuit-breaker")
    public String circuitBreaker() {
        return service.breakerDemo();
    }

    @GetMapping("/time-limiter")
    public CompletableFuture<String> timeLimiter() {
        return service.timeLimiterDemo();
    }

    @GetMapping("/bulkhead")
    public String bulkhead() {
        return service.bulkheadDemo();
    }

    @GetMapping("/rate-limiter")
    public String rateLimiter() {
        return service.rateLimiterDemo();
    }
}