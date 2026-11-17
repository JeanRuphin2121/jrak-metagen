package io.metagen.engine.meta;

import io.metagen.core.domain.MetaEntity;
import io.metagen.core.domain.MetaPermission;
import io.metagen.core.repository.MetaEntityRepository;
import io.metagen.core.repository.MetaPermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * Read-side access to the metadata model for the runtime engine, with caching.
 *
 * <p>The cache is driven explicitly through the {@link CacheManager} rather than
 * with {@code @Cacheable}: the previous annotation-based version was called from
 * inside its own class, which bypasses Spring's proxy and silently disables the
 * cache. Entities are loaded with every association the engine reads, so the
 * detached instances are safe to share between requests.
 *
 * <p>Any metadata change must call {@link #evictAll()}; eviction is deferred to
 * transaction commit so a concurrent request cannot re-cache the old state.
 */
@Slf4j
@Service
public class MetaModelService {

    public static final String ENTITY_CACHE     = "entityMeta";
    public static final String PERMISSION_CACHE = "rbacPermissions";

    private final MetaEntityRepository     entityRepository;
    private final MetaPermissionRepository permissionRepository;
    private final CacheManager             cacheManager;   // null when caching is not configured

    public MetaModelService(MetaEntityRepository entityRepository,
                            MetaPermissionRepository permissionRepository,
                            ObjectProvider<CacheManager> cacheManager) {
        this.entityRepository     = entityRepository;
        this.permissionRepository = permissionRepository;
        this.cacheManager         = cacheManager.getIfAvailable();
    }

    /** Fully-loaded entity definition (fields, workflow, states, transitions). */
    @Transactional(readOnly = true)
    public MetaEntity entity(String name) {
        return cached(ENTITY_CACHE, name, () -> {
            MetaEntity entity = entityRepository.findByNameWithAll(name)
                .orElseThrow(() -> new NoSuchElementException("Unknown entity: " + name));
            // Touch what the engine reads so nothing is lazily loaded after detachment.
            entity.getFields().forEach(f -> { f.getOptions(); });
            if (entity.getWorkflow() != null) {
                entity.getWorkflow().getStates().size();
                entity.getWorkflow().getTransitions().forEach(t -> {
                    t.getFromState().getName();
                    t.getToState().getName();
                });
            }
            return entity;
        });
    }

    /** Permissions held by any of the given roles on one entity. */
    @Transactional(readOnly = true)
    public List<MetaPermission> permissions(List<String> roles, String entityName) {
        if (roles == null || roles.isEmpty()) return List.of();
        List<String> sorted = roles.stream().sorted().toList();
        return cached(PERMISSION_CACHE, sorted + ":" + entityName,
            () -> List.copyOf(permissionRepository.findByRoleNamesAndEntityName(sorted, entityName)));
    }

    /** Drops every cached definition — immediately, or at commit if a transaction is active. */
    public void evictAll() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clear();
                }
            });
            // Also clear now: this transaction must not read its own stale cache.
        }
        clear();
    }

    private void clear() {
        if (cacheManager == null) return;
        for (String name : List.of(ENTITY_CACHE, PERMISSION_CACHE)) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
        log.debug("Metadata caches cleared");
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String cacheName, String key, Supplier<T> loader) {
        Cache cache = cacheManager != null ? cacheManager.getCache(cacheName) : null;
        if (cache == null) return loader.get();
        Cache.ValueWrapper hit = cache.get(key);
        if (hit != null) return (T) hit.get();
        T value = loader.get();
        cache.put(key, value);
        return value;
    }
}
