package io.metagen.core.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A platform user — someone who can authenticate and interact with Metagen.
 *
 * <p>Users are assigned {@link MetaRole roles}. Those roles determine what
 * they can see and do via the RBAC engine.</p>
 *
 * <h2>Password storage</h2>
 * The {@code password} field stores a BCrypt hash — never a plain-text password.
 * Use {@code PasswordEncoder.encode(rawPassword)} before persisting.
 *
 * <h2>Seeded admin user</h2>
 * An initial admin user is created on first startup by the
 * {@code DataInitializer} component using the {@code METAGEN_ADMIN_PASSWORD}
 * environment variable.
 */
@Entity
@Table(
    name = "platform_user",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_username", columnNames = "username"),
        @UniqueConstraint(name = "uq_user_email",    columnNames = "email")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String username;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String email;

    /** BCrypt hash — never store plain text. */
    @NotBlank
    @Column(nullable = false, length = 255)
    private String password;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Roles assigned to this user.
     * Loaded eagerly — needed on every authenticated request.
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_role",
        joinColumns        = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<MetaRole> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Returns role names as strings — used by JwtService. */
    public java.util.List<String> roleNames() {
        return roles.stream().map(MetaRole::getName).toList();
    }
}
