package io.metagen.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * In-memory cache configuration using Caffeine.
 *
 * <h2>Caches</h2>
 * <ul>
 *   <li>{@code entityMeta}    — entity + fields + workflow metadata.
 *       Evicted explicitly when Studio modifies an entity.
 *       Max size 500 entries, TTL 10 minutes as a safety net.</li>
 *   <li>{@code rbacPermissions} — resolved permissions per (roles, entity) pair.
 *       Evicted when permissions change in the Studio.
 *       Max size 1000 entries, TTL 5 minutes.</li>
 * </ul>
 *
 * <p>Replace with Redis-backed cache for multi-instance deployments
 * by swapping this config for a {@code RedisCacheManager} bean.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        var manager = new CaffeineCacheManager("entityMeta", "rbacPermissions");
        manager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
        );
        return manager;
    }
}
