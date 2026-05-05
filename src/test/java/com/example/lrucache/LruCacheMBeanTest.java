package com.example.lrucache;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class LruCacheMBeanTest {
    @Test
    void mbeanReportsMetrics() throws Exception {
        try (LruCache<String, String> cache = new LruCache<>(1)) {
            cache.put("a", "1");
            cache.get("a");
            cache.get("missing");
            cache.put("b", "2");

            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("com.example.lrucache:type=LruCache,name="
                    + Integer.toHexString(System.identityHashCode(cache)));

            assertTrue(server.isRegistered(name));
            assertEquals(1L, server.getAttribute(name, "Hits"));
            assertEquals(1L, server.getAttribute(name, "Misses"));
            assertEquals(1L, server.getAttribute(name, "Evictions"));
            assertEquals(1, server.getAttribute(name, "Size"));
            assertEquals(0.5d, (Double) server.getAttribute(name, "HitRatio"), 0.0001d);
        }
    }
}
