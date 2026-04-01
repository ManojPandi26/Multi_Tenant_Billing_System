package com.mtbs.auth.service;

import com.mtbs.auth.dto.user.*;
import com.mtbs.auth.entity.User;
import com.mtbs.shared.enums.notification.NotificationEvent;
import com.mtbs.auth.event.AuthEventPublisher;
import com.mtbs.shared.event.auth.AuthNotificationEvent;
import com.mtbs.shared.exception.ResourceException;
import com.mtbs.tenant.service.PlanLimitService;
import com.mtbs.tenant.service.TenantService;
import com.mtbs.shared.util.SecurityUtils;
import com.mtbs.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service for handling User operations.
 * TenantContext is automatically set by JwtAuthenticationFilter for all
 * endpoints mapped to this service.
 * Write operations are properly delegated to the transactional
 * UserScopedService bean.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserScopedService userScopedService;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;
    private final AuthEventPublisher authEventPublisher;
    private final PlanLimitService planLimitService;

    public Page<UserResponse> getAllUsers(Pageable pageable) {
        log.info("Fetching all users in current schema context");
        return userRepository.findAllWithRole(pageable).map(this::mapToResponse);
    }

    public UserResponse getUserById(Long userId) {
        log.info("Fetching user id: {}", userId);
        User user = userRepository.findByIdWithRole(userId)
                .orElseThrow(() -> ResourceException.notFound("User", String.valueOf(userId)));
        return mapToResponse(user);
    }

    public UserResponse createUser(CreateUserRequest request) {
        log.info("Delegating user creation: {}", request.getEmail());
        planLimitService.enforceUserLimit(); // ADD THIS — before any save
        User user = userScopedService.saveNewUser(request, passwordEncoder);
        return mapToResponse(user);
    }

    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        log.info("Delegating user update: {}", userId);
        User user = userScopedService.updateExistingUser(userId, request);
        return mapToResponse(user);
    }

    public void deleteUser(Long userId) {
        log.info("Delegating user deletion: {}", userId);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        userScopedService.softDeleteUser(userId, currentUserId);
    }

    public UserResponse changeUserRole(Long userId, ChangeUserRoleRequest request) {
        log.info("Delegating role change for user: {}", userId);
        User user = userScopedService.changeRole(userId, request);
        return mapToResponse(user);
    }

    public UserResponse changeUserStatus(Long userId, ChangeUserStatusRequest request) {
        log.info("Delegating status change for user: {}", userId);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User user = userScopedService.changeStatus(userId, request.getStatus(), currentUserId);
        return mapToResponse(user);
    }

    public UserResponse getMyProfile() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        log.info("Fetching own profile for user: {}", currentUserId);
        return getUserById(currentUserId);
    }

    public UserResponse updateMyProfile(UpdateOwnProfileRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        log.info("Delegating profile update for user: {}", currentUserId);
        User user = userScopedService.updateProfile(currentUserId, request, passwordEncoder);
        // 3. Fire
        authEventPublisher.publish(AuthNotificationEvent.builder()
                .eventType(NotificationEvent.PASSWORD_CHANGED)
                .recipientEmail(user.getEmail())
                .recipientName(user.getName())
                .tenantName(tenantService.fetchTenantName())
                .eventTime(Instant.now())
                .build());
        return mapToResponse(user);
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
