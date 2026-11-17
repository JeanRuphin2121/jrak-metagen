package io.metagen.core.repository;

import io.metagen.core.domain.MetaPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Data access for {@link MetaPermission}. */
@Repository
public interface MetaPermissionRepository extends JpaRepository<MetaPermission, UUID> {

    Optional<MetaPermission> findByRoleIdAndEntityId(UUID roleId, UUID entityId);

    @Query("SELECT p FROM MetaPermission p JOIN FETCH p.role JOIN FETCH p.entity WHERE p.entity.id = :entityId")
    List<MetaPermission> findByEntityId(@Param("entityId") UUID entityId);

    @Query("SELECT p FROM MetaPermission p JOIN FETCH p.role JOIN FETCH p.entity WHERE p.role.id = :roleId")
    List<MetaPermission> findByRoleId(@Param("roleId") UUID roleId);

    @Query("SELECT p FROM MetaPermission p JOIN FETCH p.role r JOIN FETCH p.entity WHERE r.name IN :roleNames")
    List<MetaPermission> findByRoleNames(@Param("roleNames") List<String> roleNames);

    /**
     * Fetch all permissions for a set of role names and a given entity name.
     * This is the hot path called on every API request — keep it fast.
     */
    @Query("""
        SELECT p FROM MetaPermission p
        JOIN FETCH p.role r
        JOIN FETCH p.entity e
        WHERE r.name IN :roleNames
          AND e.name = :entityName
        """)
    List<MetaPermission> findByRoleNamesAndEntityName(
        @Param("roleNames") List<String> roleNames,
        @Param("entityName") String entityName
    );
}
