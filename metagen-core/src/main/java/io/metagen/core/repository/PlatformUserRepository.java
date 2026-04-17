package io.metagen.core.repository;

import io.metagen.core.domain.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Data access for {@link PlatformUser}. */
@Repository
public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {

    /** Used by AuthService for login — loads roles eagerly via JOIN FETCH. */
    @Query("SELECT u FROM PlatformUser u LEFT JOIN FETCH u.roles WHERE u.username = :username")
    Optional<PlatformUser> findByUsernameWithRoles(String username);

    Optional<PlatformUser> findByUsername(String username);
    Optional<PlatformUser> findByEmail(String email);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
