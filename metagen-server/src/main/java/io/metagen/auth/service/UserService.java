package io.metagen.auth.service;

import io.metagen.auth.dto.CreateUserRequest;
import io.metagen.core.domain.MetaRole;
import io.metagen.core.domain.PlatformUser;
import io.metagen.core.repository.MetaRoleRepository;
import io.metagen.core.repository.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Platform user management — create, list, delete, and manage roles.
 *
 * <p>Only ADMIN users may call these operations (enforced at the controller layer).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final PlatformUserRepository userRepository;
    private final MetaRoleRepository     roleRepository;
    private final PasswordEncoder        passwordEncoder;

    // ── List ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PlatformUser> listAll() {
        return userRepository.findAllWithRoles();
    }

    // ── Create ────────────────────────────────────────────────────────

    @Transactional
    public PlatformUser create(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Username '" + req.username() + "' already exists");
        }

        PlatformUser user = PlatformUser.builder()
            .username(req.username())
            .email(req.email())
            .password(passwordEncoder.encode(req.password()))
            .enabled(true)
            .build();

        resolveRoles(req.roles()).forEach(r -> user.getRoles().add(r));

        PlatformUser saved = userRepository.save(user);
        log.info("Created user '{}'", saved.getUsername());
        return saved;
    }

    // ── Delete ────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        PlatformUser user = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("User " + id + " not found"));

        if ("admin".equals(user.getUsername())) {
            throw new IllegalStateException("Cannot delete the built-in admin user");
        }
        userRepository.delete(user);
        log.info("Deleted user '{}'", user.getUsername());
    }

    // ── Set roles ─────────────────────────────────────────────────────

    @Transactional
    public PlatformUser setRoles(UUID id, List<String> roleNames) {
        PlatformUser user = userRepository.findByIdWithRoles(id)
            .orElseThrow(() -> new NoSuchElementException("User " + id + " not found"));

        user.getRoles().clear();
        resolveRoles(roleNames).forEach(r -> user.getRoles().add(r));

        PlatformUser saved = userRepository.save(user);
        log.info("Updated roles for '{}': {}", saved.getUsername(), roleNames);
        return saved;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private List<MetaRole> resolveRoles(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return roleRepository.findByName("USER")
                .map(List::of)
                .orElse(List.of());
        }
        return roleNames.stream()
            .map(name -> roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + name)))
            .toList();
    }
}
