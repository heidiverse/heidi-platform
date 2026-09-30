// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.data.service.UserService;
import org.heidiverse.heidi.entity.model.entity.UserEntity;
import org.heidiverse.heidi.entity.model.user.UserProfile;
import org.heidiverse.heidi.entity.model.user.UserProfileRequest;
import org.heidiverse.heidi.entity.service.utils.JwtUtils;
import org.heidiverse.heidi.entity.service.utils.ThumbnailUtils;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management/v1/user")
@CrossOrigin(originPatterns = "*")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get user profile")
    @GetMapping("/me")
    public ResponseEntity<UserProfile> getUserProfile() {
        // Retrieve user profile based on JWT
        var jwtUserProfile = JwtUtils.getUserProfile();

        // Retrieve user metadata from DB
        var user = userService.getUser(jwtUserProfile.sub());
        var displayName =
                user.map(UserEntity::getDisplayName)
                        .filter(name -> !name.isEmpty())
                        .orElseGet(
                                () ->
                                        jwtUserProfile.displayName() != null
                                                        && !jwtUserProfile.displayName().isEmpty()
                                                ? jwtUserProfile.displayName()
                                                : jwtUserProfile.username());

        var thumbnail = user.map(UserEntity::getThumbnail).orElse("");

        // Return user profile
        UserProfile userProfile =
                new UserProfile(
                        jwtUserProfile.sub(),
                        jwtUserProfile.username(),
                        jwtUserProfile.tenantId(),
                        jwtUserProfile.permissions(),
                        displayName,
                        thumbnail);

        return ResponseEntity.ok(userProfile);
    }

    @Operation(summary = "Update user profile")
    @PostMapping("/me")
    public ResponseEntity<Void> updateUserProfile(@Valid @RequestBody UserProfileRequest request) {
        final var userId = JwtUtils.getUserId();

        // Validate base64 thumbnail if present
        if (request.getThumbnail() != null && !request.getThumbnail().isEmpty()) {
            ThumbnailUtils.validateThumbnail(request.getThumbnail());
        }

        userService.upsertUser(userId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete user")
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        // Note: ideally admins should only be allowed to delete users
        // from their tenant
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
