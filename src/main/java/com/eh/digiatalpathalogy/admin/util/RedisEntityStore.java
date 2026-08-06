package com.eh.digiatalpathalogy.admin.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class RedisEntityStore {


    private static final Logger log = LoggerFactory.getLogger(RedisEntityStore.class);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String ERROR_REDIS_NOT_AVAILABLE = "Redis unavailable: skipping cache get for key: {}";
    private static final String ERROR_NULL_KEY = "Attempted to fetch [{}] with null key";

    public RedisEntityStore(ReactiveRedisTemplate<String, Object> redisTemplate,
                            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private boolean isRedisUnavailable(Throwable e) {
        return e instanceof RedisConnectionFailureException
                || e instanceof RedisSystemException
                || e instanceof RedisException
                || (e.getCause() instanceof RedisException);
    }

    private <T> Mono<Boolean> safeSet(String key, T value) {
        return redisTemplate.opsForValue().set(key, value)
                .doOnSuccess(s -> log.debug("Saved [{}] to Redis with key: {}", value.getClass().getSimpleName(), key))
                .onErrorResume(this::isRedisUnavailable, e -> Mono.just(false))
                .doOnError(e -> log.error("Failed to save [{}] to Redis with key: {}", value.getClass().getSimpleName(), key, e));
    }

    private <T> Mono<T> safeGet(String key, Class<T> type) {
        return redisTemplate.opsForValue().get(key)
                .map(obj -> objectMapper.convertValue(obj, type))
                .onErrorResume(this::isRedisUnavailable, e -> {
                    log.warn(ERROR_REDIS_NOT_AVAILABLE, key);
                    return Mono.empty();
                })
                .doOnError(e -> log.error("Failed to fetch [{}] from Redis with key: {}", type.getSimpleName(), key, e));
    }

    private Mono<Object> safeGet(String key) {
        if (key == null) {
            log.warn("Attempted to fetch with null key");
            return Mono.empty();
        }
        return redisTemplate.opsForValue().get(key)
                .onErrorResume(this::isRedisUnavailable, e -> {
                    log.warn(ERROR_REDIS_NOT_AVAILABLE, key);
                    return Mono.empty();
                });
    }

    private Mono<List<String>> safeKeysAsList(String pattern) {
        return redisTemplate.keys(pattern)
                .collectList()
                .onErrorResume(this::isRedisUnavailable, e ->{
                    log.warn(ERROR_REDIS_NOT_AVAILABLE, pattern);
                    return Mono.just(Collections.emptyList());
                } )
                .doOnError(e -> log.error("Error scanning keys for pattern '{}'", pattern, e));
    }

    public <T> Mono<Boolean> save(String key, T value) {
        if (key == null || value == null) {
            log.warn("Attempted to save null key or value");
            return Mono.just(false);
        }
        return safeSet(key, value);
    }

    public <T> Mono<T> findByKey(String key, Class<T> type) {
        if (key == null) {
            log.warn(ERROR_NULL_KEY, type.getSimpleName());
            return Mono.empty();
        }
        return safeGet(key, type)
                .doOnNext(val -> log.debug("Fetched [{}] from Redis with key: {}", type.getSimpleName(), key));
    }

    public <T> Mono<T> findByKeyWithFallback(String key, Supplier<Mono<T>> dbSupplier, Class<T> type) {
        if (key == null) {
            log.warn(ERROR_NULL_KEY, type.getSimpleName());
            return Mono.empty();
        }
        return safeGet(key, type)
                .flatMap(cached -> {
                    log.info("Cache hit: [{}] found in Redis with key: {}", type.getSimpleName(), key);
                    return Mono.just(cached);
                })
                .switchIfEmpty(
                        dbSupplier.get()
                                .flatMap(fresh -> safeSet(key, fresh).thenReturn(fresh))
                                .doOnSuccess(val -> log.info("Cache miss: fetched [{}] from DB and cached with key: {}", type.getSimpleName(), key))
                                .doOnError(e -> log.error("Failed to fetch [{}] from DB for key: {}", type.getSimpleName(), key, e))
                )
                .doOnError(e -> log.error("Error fetching [{}] from Redis with key: {}", type.getSimpleName(), key, e));
    }

    public <T> Mono<List<T>> findByPatternWithFallback(String patternPrefix, Predicate<T> filter, Supplier<Flux<T>> dbSupplier, Function<T, String> keyExtractor, Class<T> type) {

        String pattern = patternPrefix + "*";
        return safeKeysAsList(pattern)
                .flatMapMany(Flux::fromIterable)
                .flatMap(key -> safeGet(key)
                        .map(obj -> objectMapper.convertValue(obj, type))
                        .filter(filter)
                        .onErrorResume(e -> {
                            log.error("Error fetching [{}] for key: {}", type.getSimpleName(), key, e);
                            return Mono.empty();
                        }))
                .collectList()
                .flatMap(cachedList -> {
                    if (!cachedList.isEmpty()) {
                        log.info("Cache hit: {} [{}] items found by pattern", cachedList.size(), type.getSimpleName());
                        return Mono.just(cachedList);
                    }
                    log.warn("Cache miss: fetching [{}] from DB by pattern", type.getSimpleName());
                    return dbSupplier.get()
                            .collectList()
                            .flatMap(freshList -> Flux.fromIterable(freshList)
                                    .flatMap(item -> {
                                        String key = patternPrefix + keyExtractor.apply(item);
                                        return safeSet(key, item);
                                    })
                                    .then(Mono.just(freshList)));
                });
    }

    public Mono<Boolean> deleteByKey(String key) {
        if (key == null) {
            log.warn("Attempted to delete null key");
            return Mono.just(false);
        }
        return redisTemplate.delete(key)
                .map(count -> count > 0)
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.info("Deleted entry from Redis with key: {}", key);
                    }
                })
                .onErrorResume(this::isRedisUnavailable, e -> {
                    log.warn("Redis unavailable: skipping delete for key: {}", key);
                    return Mono.just(false);
                })
                .doOnError(e -> log.error("Failed to delete entry from Redis with key: {}", key, e));
    }

    public Mono<Boolean> deleteKeysByPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            log.warn("Attempted to delete keys with null or empty pattern");
            return Mono.just(false);
        }
        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete).count()
                .map(count -> {
                    log.info("Total keys deleted for pattern '{}': {}", pattern, count);
                    return count > 0;
                })
                .onErrorResume(this::isRedisUnavailable, e -> {
                    log.warn("Redis unavailable: skipping delete by pattern '{}'", pattern);
                    return Mono.just(false);
                })
                .onErrorResume(e -> {
                    log.error("Failed to delete keys by pattern '{}': {}", pattern, e.getMessage(), e);
                    return Mono.just(false);
                });
    }

    public Mono<Map<String, Object>> findByPattern(String patternPrefix) {
        String pattern = patternPrefix + "*";

        return safeKeysAsList(pattern)
                .flatMapMany(Flux::fromIterable)
                .flatMap(key -> safeGet(key)
                        .map(value -> Map.entry(key, value))
                        .onErrorResume(e -> {
                            log.error("Error fetching value for key: {}", key, e);
                            return Mono.empty();
                        }))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public Mono<Map<String, Object>> fetchApplicationConfig(String appName) {
        String key = String.format("config:%s", appName);

        return safeGet(key)
                .flatMap(value -> Mono.just((Map<String, Object>) value))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("No value found for key: {}", key);
                    return Mono.just(Collections.emptyMap());
                }))
                .doOnSuccess(resultMap -> {
                    if (resultMap.isEmpty()) {
                        log.warn("Empty config map retrieved for key: {}", key);
                    } else {
                        log.info("Successfully retrieved config map for key: {}", key);
                    }
                })
                .onErrorResume(this::isRedisUnavailable, e -> {
                    log.warn("Redis unavailable: returning empty config for key: {}", key);
                    return Mono.just(Collections.emptyMap());
                })
                .doOnError(e -> log.error("Error retrieving config for key: {}", key, e));
    }

    public Mono<Boolean> acquireLock(String key, String value, Duration ttl) {

        return redisTemplate.opsForValue()
                .setIfAbsent(key, value, ttl)
                .onErrorResume(e -> {
                    log.error("Failed to acquire lock key={}", key, e);
                    return Mono.just(false);
                });
    }

    public Mono<Boolean> releaseLock(String key, String value) {

        String script =
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else return 0 end";

        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);

        return redisTemplate.execute(redisScript, List.of(key), value)
                .next()
                .map(result -> result != null && result == 1L)
                .onErrorResume(e -> {
                    log.error("Failed to release lock key={}", key, e);
                    return Mono.just(false);
                });
    }


    public Mono<Boolean> add(String key, String value, double score) {
        return redisTemplate.opsForZSet()
                .add(key, value, score)
                .onErrorResume(e -> {
                    log.error("ZSET add failed key={}, value={}", key, value, e);
                    return Mono.just(false);
                });
    }

    public Mono<Boolean> remove(String key, String value) {
        return redisTemplate.opsForZSet()
                .remove(key, value)
                .map(count -> count > 0)
                .onErrorReturn(false);
    }

    public Flux<String> getExpired(String key, long thresholdScore) {

        Range<Double> range = Range.closed(0.0, (double) thresholdScore);

        return redisTemplate.opsForZSet()
                .rangeByScore(key, range, Limit.unlimited())
                .cast(String.class)
                .onErrorResume(e -> {
                    log.error("ZSET fetch expired failed key={}", key, e);
                    return Flux.empty();
                });
    }
}
