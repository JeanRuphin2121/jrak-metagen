package io.metagen.core.repository;

import io.metagen.core.domain.MetaField;
import io.metagen.core.domain.MetaFieldType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Data access for {@link MetaField}. */
@Repository
public interface MetaFieldRepository extends JpaRepository<MetaField, UUID> {

    List<MetaField> findByEntityIdOrderByPositionAsc(UUID entityId);

    Optional<MetaField> findByEntityIdAndName(UUID entityId, String name);

    boolean existsByEntityIdAndName(UUID entityId, String name);

    List<MetaField> findByEntityIdAndType(UUID entityId, MetaFieldType type);

    /** Searchable fields for a given entity — used to build search indexes. */
    @Query("SELECT f FROM MetaField f WHERE f.entity.id = :entityId AND f.searchable = true ORDER BY f.position")
    List<MetaField> findSearchableByEntityId(@Param("entityId") UUID entityId);

    /** Shift positions to make room for an insertion. */
    @Modifying
    @Query("UPDATE MetaField f SET f.position = f.position + 1 WHERE f.entity.id = :entityId AND f.position >= :from")
    void shiftPositionsFrom(@Param("entityId") UUID entityId, @Param("from") int from);
}
