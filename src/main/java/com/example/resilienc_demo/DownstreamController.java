package com.example.resilienc_demo;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pretend "other service" (think: Fraud Check or Credit Bureau).
 * We make it misbehave on purpose so the resilience patterns have something to protect against.
 */
@RestController
@RequestMapping("/downstream")
public class DownstreamController {

    private static final Logger log = LoggerFactory.getLogger(DownstreamController.class);
    private final AtomicInteger flakyCalls = new AtomicInteger();

    /** Always works. */
    @GetMapping("/ok")
    public String ok() {
        return "OK from downstream";
    }

    /** Always fails, like a service that is down. */
    @GetMapping("/fail")
    public String fail() {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "downstream is down");
    }

    /** Works, but only after a delay, like a hanging partner API. */
    @GetMapping("/slow")
    public String slow(@RequestParam(defaultValue = "5000") long ms) throws InterruptedException {
        Thread.sleep(ms);
        return "slow response after " + ms + " ms";
    }

    /** Fails randomly (60% by default), like a network blip. */
    @GetMapping("/flaky")
    public String flaky(@RequestParam(defaultValue = "60") int failPercent) {
        int n = flakyCalls.incrementAndGet();
        if (ThreadLocalRandom.current().nextInt(100) < failPercent) {
            log.warn("downstream /flaky call #{} -> FAILED", n);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "temporary glitch");
        }
        log.info("downstream /flaky call #{} -> success", n);
        return "SUCCESS from flaky downstream (its call #" + n + ")";
    }
}