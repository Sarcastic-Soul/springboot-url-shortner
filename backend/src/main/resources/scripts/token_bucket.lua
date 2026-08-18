-- Distributed token bucket. One round trip, one atomic script.
--
-- Replaces a fixed-window INCR, which had two defects:
--   1. A window boundary let a caller spend a full window's budget on each side
--      of it -- 2x capacity in an instant.
--   2. INCR and EXPIRE were separate calls. A pod dying between them left a key
--      with no TTL, permanently consuming that caller's quota.
--
-- Time comes from redis.call('TIME'), not from the caller, so pods with skewed
-- clocks cannot disagree about how much a bucket has refilled.
--
-- KEYS[1] bucket key
-- ARGV[1] capacity            (max tokens)
-- ARGV[2] refillTokens        (tokens added per interval)
-- ARGV[3] refillIntervalMs    (length of that interval)
-- ARGV[4] requested           (tokens this call wants)
--
-- returns { allowed (1|0), remaining tokens }

local key             = KEYS[1]
local capacity        = tonumber(ARGV[1])
local refillTokens    = tonumber(ARGV[2])
local refillIntervalMs = tonumber(ARGV[3])
local requested       = tonumber(ARGV[4])

local time  = redis.call('TIME')
local nowMs = (tonumber(time[1]) * 1000) + math.floor(tonumber(time[2]) / 1000)

local bucket = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(bucket[1])
local ts     = tonumber(bucket[2])

if tokens == nil or ts == nil then
  tokens = capacity
  ts     = nowMs
end

local elapsed = nowMs - ts
if elapsed > 0 then
  tokens = math.min(capacity, tokens + (elapsed / refillIntervalMs) * refillTokens)
  ts     = nowMs
end

local allowed = 0
if tokens >= requested then
  tokens  = tokens - requested
  allowed = 1
end

redis.call('HSET', key, 'tokens', tokens, 'ts', ts)

-- Expire once a fully drained bucket would have refilled completely: past that
-- point the key is indistinguishable from a missing one, so keeping it wastes
-- memory. The extra interval is slack for clock granularity.
local ttlMs = math.ceil((capacity / refillTokens) * refillIntervalMs) + refillIntervalMs
redis.call('PEXPIRE', key, ttlMs)

return { allowed, math.floor(tokens) }
