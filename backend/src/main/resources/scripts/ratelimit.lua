redis.call('zremrangebyscore', KEYS[1], 0, tonumber(ARGV[1]) - tonumber(ARGV[2]))
local count = redis.call('zcard', KEYS[1])
if count >= tonumber(ARGV[3]) then
  return 0
end
redis.call('zadd', KEYS[1], tonumber(ARGV[1]), ARGV[4])
redis.call('expire', KEYS[1], math.floor(tonumber(ARGV[2]) / 1000) + 1)
return 1
