-- KEYS[1]: The Redis key for this user (e.g., "rate:alex")
-- ARGV[1]: Max bucket capacity (e.g., 5)
-- ARGV[2]: Refill rate per second (e.g., 1)
-- ARGV[3]: Current timestamp in seconds

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- 1. Grab the current state from the Redis Hash
local data = redis.call("HMGET", key, "tokens", "last_updated")
local tokens = tonumber(data[1])
local last_updated = tonumber(data[2])

if not tokens then
    -- First time seeing this user: Fill the bucket completely
    tokens = capacity
    last_updated = now
else
    -- 2. "Lazy Refill" Math: Calculate tokens earned since last request
    local elapsed = now - last_updated
    local generated = elapsed * refill_rate
    tokens = math.min(capacity, tokens + generated)
    last_updated = now
end

-- 3. Decision Time (The Bouncer)
if tokens >= 1 then
    tokens = tokens - 1
    -- Update Redis with new token count and current timestamp
    redis.call("HMSET", key, "tokens", tokens, "last_updated", last_updated)
    return 1 -- Allowed!
else
    -- Bucket is empty, update timestamp anyway to track active traffic
    redis.call("HMSET", key, "last_updated", last_updated)
    return 0 -- Blocked!
end