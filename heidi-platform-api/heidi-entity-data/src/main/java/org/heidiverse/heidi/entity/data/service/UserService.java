// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.service;

import org.heidiverse.heidi.entity.data.repository.UserRepository;
import org.heidiverse.heidi.entity.model.entity.UserEntity;
import org.heidiverse.heidi.entity.model.user.UserProfileRequest;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public Optional<UserEntity> getUser(String userId) {
        return userRepository.findByIdAndDeletedFalse(userId);
    }

    @Transactional
    public void upsertUser(String userId, UserProfileRequest request) {
        var user =
                userRepository
                        .findById(userId)
                        .orElseGet(() -> new UserEntity(userId)); // Create a new user if not found
        user.setDisplayName(request.getDisplayName());
        user.setThumbnail(request.getThumbnail());
        user.setDeleted(false);
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(String userId) {
        userRepository
                .findById(userId)
                .ifPresent(
                        user -> {
                            user.setDeleted(true);
                            userRepository.save(user);
                        });
    }
}
