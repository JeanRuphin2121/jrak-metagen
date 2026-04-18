package io.metagen.auth.controller;

import io.metagen.auth.dto.CreateUserRequest;
import io.metagen.auth.dto.UserResponse;
import io.metagen.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Platform user management REST API — ADMIN only.
 *
 * <pre>
 * GET    /api/studio/users              — list all users
 * POST   /api/studio/users              — create a user
 * DELETE /api/studio/users/{id}         — delete a user
 * PUT    /api/studio/users/{id}/roles   — replace a user's roles
 * </pre>
 */
@RestController
@RequestMapping("/api/studio/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Studio API — Users", description = "Manage platform users and role assignments (ADMIN only)")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List all platform users")
    public List<UserResponse> listAll() {
        return userService.listAll().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a platform user")
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        return UserResponse.from(userService.create(req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a platform user (built-in admin cannot be deleted)")
    public void delete(@PathVariable UUID id) {
        userService.delete(id);
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Replace the role list for a user")
    public UserResponse setRoles(@PathVariable UUID id, @RequestBody List<String> roleNames) {
        return UserResponse.from(userService.setRoles(id, roleNames));
    }
}
