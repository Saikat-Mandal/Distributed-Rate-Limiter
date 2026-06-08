package com.application.ratelimiter.controller;


import com.application.ratelimiter.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RateLimiterController {

    @Autowired
    private RateLimiterService rateLimiterService;

    @GetMapping("/secure-data")
    public ResponseEntity<String> getSecureData(@RequestParam String userId) {
        // DEFINE THE RULES FOR THIS ENDPOINT:
        // Max bucket capacity = 5 tokens
        // Refill rate = 1 token per second
        int maxCapacity = 5;
        int refillRate = 1;

        // Ask our Redis service if this user is allowed through
        boolean allowed = rateLimiterService.isAllowed(userId, maxCapacity, refillRate);

        if(allowed){
            // Path A: Token was available and consumed
            return ResponseEntity.ok("Access Granted! Here is your highly secure data.");
        }
        else{
            // Path B: Bucket empty, block immediately with HTTP 429
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("HTTP Error 429: Too Many Requests. Please slow down!");
        }
    }

}
