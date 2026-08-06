package com.eh.digiatalpathalogy.admin.util;

import com.eh.digiatalpathalogy.admin.entity.QaSlide;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.eh.digiatalpathalogy.admin.testdata.QaSlideTestData.pathQaSlide;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisEntityStoreTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ReactiveRedisTemplate<String, Object> redisTemplate;
    @Mock
    private ReactiveValueOperations<String, Object> valueOps;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private RedisEntityStore store;

    private static final String KEY_PREFIX = "prefix::";
    private static final QaSlide SLIDE = pathQaSlide();
    private static final String CACHE_KEY = KEY_PREFIX + SLIDE.barcode();

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

    }

    // --- Simple DTO for conversion tests ---
    record User(String id, String name) {
    }

    // ---------- save ----------
    @Test
    @DisplayName("save: should return true when Redis set succeeds")
    void save_ok() {
        when(valueOps.set(CACHE_KEY, SLIDE)).thenReturn(Mono.just(true));

        StepVerifier.create(store.save(CACHE_KEY, SLIDE))
                .expectNext(true)
                .verifyComplete();

        verify(valueOps).set(eq(CACHE_KEY), argThat(o -> ((QaSlide) o).barcode().equals(SLIDE.barcode())));
    }

    @Test
    @DisplayName("save: should return false for null key or value")
    void save_nulls() {
        StepVerifier.create(store.save(null, SLIDE))
                .expectNext(false)
                .verifyComplete();

        StepVerifier.create(store.save(CACHE_KEY, null))
                .expectNext(false)
                .verifyComplete();

        verifyNoInteractions(valueOps);
    }

    // ---------- findByKey ----------
    @Test
    @DisplayName("findByKey: returns mapped value when present")
    void findByKey_present() {

        when(valueOps.get(CACHE_KEY)).thenReturn(Mono.just(SLIDE));
        StepVerifier.create(store.findByKey(CACHE_KEY, QaSlide.class))
                .assertNext(qaSlide -> {
                    assertEquals("10224", qaSlide.barcode());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findByKey: returns empty when key not found")
    void findByKey_absent() {
        when(valueOps.get(CACHE_KEY)).thenReturn(Mono.empty());

        StepVerifier.create(store.findByKey(CACHE_KEY, QaSlide.class))
                .verifyComplete();
    }

    @Test
    @DisplayName("findByKey: returns empty for null key")
    void findByKey_nullKey() {
        StepVerifier.create(store.findByKey(null, QaSlide.class))
                .verifyComplete();

        verifyNoInteractions(valueOps);
    }

    // ---------- findByKeyWithFallback ----------
    @Test
    @DisplayName("findByKeyWithFallback: cache hit short-circuits DB")
    void findByKeyWithFallback_cacheHit() {

        when(valueOps.get(CACHE_KEY)).thenReturn(Mono.just(SLIDE));

        Supplier<Mono<QaSlide>> db = () -> Mono.error(new IllegalStateException("Should not call DB"));
        StepVerifier.create(store.findByKeyWithFallback(CACHE_KEY, db, QaSlide.class))
                .assertNext(qaSlide -> {
                    assertEquals("10224", qaSlide.barcode());
                })
                .verifyComplete();

        verify(valueOps, never()).set(anyString(), any());
    }

    @Test
    @DisplayName("findByKeyWithFallback: cache miss → fetch from DB and cache")
    void findByKeyWithFallback_cacheMiss_thenDb() {

        when(valueOps.get(CACHE_KEY)).thenReturn(Mono.empty());
        when(valueOps.set(CACHE_KEY, SLIDE)).thenReturn(Mono.just(true));

        Supplier<Mono<QaSlide>> db = () -> Mono.just(SLIDE);
        StepVerifier.create(store.findByKeyWithFallback(CACHE_KEY, db, QaSlide.class))
                .assertNext(u -> {
                    assertEquals("10224", u.barcode());
                })
                .verifyComplete();

        verify(valueOps).set(CACHE_KEY, SLIDE);
    }

    @Test
    @DisplayName("findByKeyWithFallback: null key → empty")
    void findByKeyWithFallback_nullKey() {
        Supplier<Mono<QaSlide>> db = () -> Mono.error(new AssertionError("DB should not be called"));

        StepVerifier.create(store.findByKeyWithFallback(null, db, QaSlide.class))
                .verifyComplete();

        verifyNoInteractions(valueOps);
    }

    // ---------- findByPatternWithFallback ----------
    @Test
    @DisplayName("findByPatternWithFallback: cache hit returns list and does not call DB")
    void findByPatternWithFallback_cacheHit() {
        String prefix = "u:";
        Set<String> keys = Set.of("u:1", "u:2");

        when(redisTemplate.keys(prefix + "*")).thenReturn(Flux.fromIterable(keys));
        when(valueOps.get("u:1")).thenReturn(Mono.just(Map.of("id", "1", "name", "Raj")));
        when(valueOps.get("u:2")).thenReturn(Mono.just(Map.of("id", "2", "name", "Zoe")));

        Predicate<User> all = u -> true;
        Supplier<Flux<User>> db = () -> Flux.error(new IllegalStateException("DB should not be called"));
        Function<User, String> keyExtractor = User::id;

        StepVerifier.create(
                        store.findByPatternWithFallback(prefix, all, db, keyExtractor, User.class)
                )
                .expectNextMatches(list -> list.size() == 2 &&
                        list.stream().anyMatch(u -> u.id().equals("1")) &&
                        list.stream().anyMatch(u -> u.id().equals("2")))
                .verifyComplete();

        verify(valueOps, never()).set(anyString(), any());
    }

    @Test
    @DisplayName("findByPatternWithFallback: cache miss → fetch list from DB and cache each item")
    void findByPatternWithFallback_cacheMiss_thenDb() {
        String prefix = "u:";
        when(redisTemplate.keys(prefix + "*")).thenReturn(Flux.empty());

        List<User> fresh = List.of(new User("10", "Ten"), new User("11", "Eleven"));
        when(valueOps.set("u:10", fresh.get(0))).thenReturn(Mono.just(true));
        when(valueOps.set("u:11", fresh.get(1))).thenReturn(Mono.just(true));

        Predicate<User> all = u -> true;
        Supplier<Flux<User>> db = () -> Flux.fromIterable(fresh);
        Function<User, String> keyExtractor = User::id;

        StepVerifier.create(
                        store.findByPatternWithFallback(prefix, all, db, keyExtractor, User.class)
                )
                .expectNext(fresh)
                .verifyComplete();

        verify(valueOps).set("u:10", fresh.get(0));
        verify(valueOps).set("u:11", fresh.get(1));
    }

    @Test
    @DisplayName("findByPatternWithFallback: filters cached values using provided predicate")
    void findByPatternWithFallback_filtering() {
        String prefix = "u:";
        when(redisTemplate.keys(prefix + "*")).thenReturn(Flux.just("u:1", "u:2", "u:3"));

        when(valueOps.get("u:1")).thenReturn(Mono.just(Map.of("id", "1", "name", "A")));
        when(valueOps.get("u:2")).thenReturn(Mono.just(Map.of("id", "2", "name", "B")));
        when(valueOps.get("u:3")).thenReturn(Mono.just(Map.of("id", "3", "name", "C")));

        Predicate<User> onlyB = u -> "B".equals(u.name());
        Supplier<Flux<User>> db = Flux::<User>empty; // will not be called because cache hit
        Function<User, String> keyExtractor = User::name;

        StepVerifier.create(
                        store.findByPatternWithFallback(prefix, onlyB, db, keyExtractor, User.class)
                )
                .expectNextMatches(list -> list.size() == 1 && "B".equals(list.get(0).name()))
                .verifyComplete();
    }

    // ---------- deleteByKey ----------
    @Test
    @DisplayName("deleteByKey: returns true when a key was deleted")
    void deleteByKey_deleted() {
        when(redisTemplate.delete("k")).thenReturn(Mono.just(1L));

        StepVerifier.create(store.deleteByKey("k"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("deleteByKey: returns false when no key deleted")
    void deleteByKey_notFound() {
        when(redisTemplate.delete("missing")).thenReturn(Mono.just(0L));

        StepVerifier.create(store.deleteByKey("missing"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("deleteByKey: null key → false")
    void deleteByKey_null() {
        StepVerifier.create(store.deleteByKey(null))
                .expectNext(false)
                .verifyComplete();

        verify(redisTemplate, never()).delete(anyString());
    }

    // ---------- deleteKeysByPattern ----------
    @Test
    @DisplayName("deleteKeysByPattern: deletes matching keys and returns true when count > 0")
    void deleteKeysByPattern_ok() {
        when(redisTemplate.keys("u:*")).thenReturn(Flux.just("u:1", "u:2", "u:3"));
        when(redisTemplate.delete("u:1")).thenReturn(Mono.just(1L));
        when(redisTemplate.delete("u:2")).thenReturn(Mono.just(1L));
        when(redisTemplate.delete("u:3")).thenReturn(Mono.just(1L));

        StepVerifier.create(store.deleteKeysByPattern("u:*"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("deleteKeysByPattern: returns false for null or empty pattern")
    void deleteKeysByPattern_nullOrEmpty() {
        StepVerifier.create(store.deleteKeysByPattern(null))
                .expectNext(false)
                .verifyComplete();

        StepVerifier.create(store.deleteKeysByPattern("  "))
                .expectNext(false)
                .verifyComplete();

        verify(redisTemplate, never()).keys(anyString());
    }

    @Test
    @DisplayName("deleteKeysByPattern: returns false on error")
    void deleteKeysByPattern_error() {
        when(redisTemplate.keys("config:*")).thenReturn(Flux.error(new RuntimeException("err")));

        StepVerifier.create(store.deleteKeysByPattern("config:*"))
                .expectNext(false)
                .verifyComplete();
    }

    // ---------- findByPattern ----------
    @Test
    @DisplayName("findByPattern: returns map of key->value")
    void findByPattern_ok() {
        when(redisTemplate.keys("config:*")).thenReturn(Flux.just("config:a", "config:b"));
        when(valueOps.get("config:a")).thenReturn(Mono.just(Map.of("x", 1)));
        when(valueOps.get("config:b")).thenReturn(Mono.just("raw"));

        StepVerifier.create(store.findByPattern("config:"))
                .expectNextMatches(map ->
                        map.size() == 2 &&
                                map.get("config:a") instanceof Map &&
                                Objects.equals(map.get("config:b"), "raw"))
                .verifyComplete();
    }

    // ---------- fetchApplicationConfig ----------
    @Test
    @DisplayName("fetchApplicationConfig: returns map when present")
    void fetchApplicationConfig_present() {
        Map<String, Object> cfg = Map.of("feature", true, "limit", 10);
        when(valueOps.get("config:eh-dicom-receiver")).thenReturn(Mono.just(cfg));

        StepVerifier.create(store.fetchApplicationConfig("eh-dicom-receiver"))
                .expectNext(cfg)
                .verifyComplete();
    }

    @Test
    @DisplayName("fetchApplicationConfig: returns empty map when not found")
    void fetchApplicationConfig_absent() {
        when(valueOps.get("config:eh-dicom-receiver")).thenReturn(Mono.empty());

        StepVerifier.create(store.fetchApplicationConfig("eh-dicom-receiver"))
                .expectNext(Collections.emptyMap())
                .verifyComplete();
    }
}
