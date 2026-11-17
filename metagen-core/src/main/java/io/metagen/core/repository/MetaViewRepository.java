package io.metagen.core.repository;

import io.metagen.core.domain.MetaView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetaViewRepository extends JpaRepository<MetaView, UUID> {

    Optional<MetaView> findByName(String name);
}
