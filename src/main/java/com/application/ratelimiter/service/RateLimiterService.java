package com.application.ratelimiter.service;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;

@Service
public class RateLimiterService {
    private final StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Long> script;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        }

    @PostConstruct
    public void init(){
        // This runs once when the app boots. It loads the Lua script from resources.
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        script.setResultType(Long.class);
    }
    public boolean isAllowed(String userId , int capacity , int refillRate){
        String key = "rate:" + userId;
        // Get current system time in seconds to pass to the script
        String now = String.valueOf(Instant.now().getEpochSecond());

        // Execute the script atomically inside Redis
        Long result = redisTemplate.execute(
                script,
                Collections.singletonList(key), // Passes to KEYS[1]
                String.valueOf(capacity),       // Passes to ARGV[1]
                String.valueOf(refillRate),     // Passes to ARGV[2]
                now                             // Passes to ARGV[3]
        );

        // If Redis returns 1, request is allowed. If 0, it's blocked.
        return result != null && result == 1;
    }

}
