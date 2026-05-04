package com.example.lrucache;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

final class LruCacheTtlTest {
    @Test
    void entryExpiresAfterTtl() throws Exception {
        try (LruCache<String, String> cache = new LruCache<>(16)) {
            cache.put("k", "v", 100, TimeUnit.MILLISECONDS);
            Thread.sleep(200L);
            assertNull(cache.get("k"));
        }
    }
}
