package io.metagen.core.repository;

import io.metagen.core.domain.MetaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link MetaEntity}.
 *
 * <p>All lookups that will feed the runtime engine should use the
 * {@code WithFields} or {@code WithAll} variants to avoid N+1 queries
 * when iterating over fields or relations.</p>
 */
@Repository
public interface MetaEntityRepository extends JpaRepository<MetaEntity, UUID> {

    /** Find by technical name (e.g. {@code supplier}). */
    Optional<MetaEntity> findByName(String name);

    /** Find by generated table name (e.g. {@code data_supplier}). */
    Optional<MetaEntity> findByTableName(String tableName);

    boolean existsByName(String name);
    boolean existsByTableName(String tableName);

    boolean existsByWorkflowId(UUID workflowId);

    /**
     * Fetch entity with its fields in a single query.
     * Use this when you need to render a form or build a CREATE TABLE statement.
     */
    @Query("""
        SELECT e FROM MetaEntity e
        LEFT JOIN FETCH e.fields f
        WHERE e.name = :name
        ORDER BY f.position ASC
        """)
    Optional<MetaEntity> findByNameWithFields(@Param("name") String name);

    /**
     * Fetch entity with all associations needed by the runtime engine
     * (fields, relations, workflow + states/transitions).
     */
    @Query("""
        SELECT DISTINCT e FROM MetaEntity e
        LEFT JOIN FETCH e.fields
        LEFT JOIN FETCH e.workflow w
        LEFT JOIN FETCH w.states
        LEFT JOIN FETCH w.transitions t
        LEFT JOIN FETCH t.fromState
        LEFT JOIN FETCH t.toState
        WHERE e.name = :name
        """)
    Optional<MetaEntity> findByNameWithAll(@Param("name") String name);
}
