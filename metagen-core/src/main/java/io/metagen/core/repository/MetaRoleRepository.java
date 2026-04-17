package io.metagen.core.repository;

import io.metagen.core.domain.MetaRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Data access for {@link MetaRole}. */
@Repository
public interface MetaRoleRepository extends JpaRepository<MetaRole, UUID> {

    Optional<MetaRole> findByName(String name);

    boolean existsByName(String name);

    List<MetaRole> findByBuiltInFalse();
}
