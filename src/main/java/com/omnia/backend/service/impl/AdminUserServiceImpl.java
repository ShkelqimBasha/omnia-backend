package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.UserRoleUpdateRequest;
import com.omnia.backend.dto.request.UserStatusUpdateRequest;
import com.omnia.backend.dto.response.AdminUserResponse;
import com.omnia.backend.dto.response.AdminUserStatsResponse;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.PlatformRoleName;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.mapper.AdminUserMapper;
import com.omnia.backend.repository.OrganizationMemberRepository;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.interfaces.AdminUserService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import com.omnia.backend.specification.UserSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.dto.request.AdminCreateUserRequest;
import com.omnia.backend.service.interfaces.EmailVerificationService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class AdminUserServiceImpl
        implements AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final List<String>
            ALLOWED_SORT_FIELDS =
            List.of(
                    "id",
                    "firstName",
                    "lastName",
                    "username",
                    "email",
                    "status",
                    "createdAt",
                    "updatedAt"
            );

    private static final Set<String>
            PLATFORM_ADMIN_ROLES =
            Set.of(
                    "ADMIN",
                    "SUPER_ADMIN"
            );

    private static final Set<OrganizationMemberRole>
            ORGANIZATION_ADMIN_ROLES =
            Set.of(
                    OrganizationMemberRole.OWNER,
                    OrganizationMemberRole.ADMIN
            );

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final OrganizationMemberRepository
            memberRepository;

    private final AdminUserMapper userMapper;

    private final CurrentUserService currentUserService;

    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    private final EmailVerificationService
            emailVerificationService;

    public AdminUserServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            OrganizationMemberRepository memberRepository,
            AdminUserMapper userMapper,
            CurrentUserService currentUserService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.memberRepository = memberRepository;
        this.userMapper = userMapper;
        this.currentUserService = currentUserService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService =
                emailVerificationService;
    }
    @Override
    @Transactional
    public AdminUserResponse createUser(
            AdminCreateUserRequest request
    ) {
        requirePlatformAdministrator();

        String normalizedEmail =
                request.getEmail()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        String normalizedUsername =
                request.getUsername().trim();

        if (userRepository.existsByEmail(
                normalizedEmail
        )) {
            throw new ResourceAlreadyExistsException(
                    "Email is already in use"
            );
        }

        if (userRepository.existsByUsername(
                normalizedUsername
        )) {
            throw new ResourceAlreadyExistsException(
                    "Username is already in use"
            );
        }

        Role userRole =
                roleRepository
                        .findByName("USER")
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Default role USER not found"
                                )
                        );

        User user =
                User.builder()
                        .firstName(
                                request.getFirstName().trim()
                        )
                        .lastName(
                                normalizeOptionalValue(
                                        request.getLastName()
                                )
                        )
                        .username(normalizedUsername)
                        .email(normalizedEmail)
                        .passwordHash(
                                passwordEncoder.encode(
                                        request.getPassword()
                                )
                        )
                        .phone(
                                normalizeOptionalValue(
                                        request.getPhone()
                                )
                        )
                        .role(userRole)
                        .status(UserStatus.ACTIVE)
                        .emailVerified(false)
                        .build();

        User savedUser =
                userRepository.saveAndFlush(user);

        AdminUserResponse response =
                userMapper.toResponse(
                        savedUser,
                        false
                );

        emailVerificationService
                .createVerificationToken(savedUser);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AdminUserResponse> getUsers(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String keyword,
            UserStatus status,
            String role
    ) {
        requirePlatformAdministrator();
        validatePagination(page, size);

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                resolveSortDirection(sortDir),
                                resolveSortField(sortBy)
                        )
                );

        Set<Long> organizationAdminUserIds =
                getOrganizationAdminUserIds();

        Page<AdminUserResponse> responsePage =
                userRepository
                        .findAll(
                                UserSpecification.filterUsers(
                                        keyword,
                                        status,
                                        role
                                ),
                                pageable
                        )
                        .map(user ->
                                userMapper.toResponse(
                                        user,
                                        organizationAdminUserIds
                                                .contains(
                                                        user.getId()
                                                )
                                )
                        );

        return PagedResponse.from(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(
            Long userId
    ) {
        requirePlatformAdministrator();

        User user = findUser(userId);

        return userMapper.toResponse(
                user,
                getOrganizationAdminUserIds()
                        .contains(user.getId())
        );
    }

    @Override
    @Transactional
    public AdminUserResponse updateUserStatus(
            Long userId,
            UserStatusUpdateRequest request
    ) {
        User currentUser =
                requirePlatformAdministrator();

        User targetUser = findUser(userId);

        UserStatus requestedStatus =
                request.getStatus();

        if (requestedStatus == null) {
            throw new IllegalArgumentException(
                    "User status is required"
            );
        }

        if (Objects.equals(
                currentUser.getId(),
                targetUser.getId()
        ) && !UserStatus.ACTIVE.equals(
                requestedStatus
        )) {
            throw new AccessDeniedException(
                    "You cannot deactivate or ban "
                            + "your own account"
            );
        }

        validateLastPlatformAdministrator(
                targetUser,
                requestedStatus
        );

        targetUser.setStatus(requestedStatus);

        User savedUser =
                userRepository.saveAndFlush(
                        targetUser
                );

        if (!UserStatus.ACTIVE.equals(
                requestedStatus
        )) {
            refreshTokenService
                    .revokeAllUserTokens(
                            savedUser.getId()
                    );
        }

        return userMapper.toResponse(
                savedUser,
                getOrganizationAdminUserIds()
                        .contains(savedUser.getId())
        );
    }

    @Override
    @Transactional
    public AdminUserResponse updateUserRole(
            Long userId,
            UserRoleUpdateRequest request
    ) {
        User currentUser =
                requirePlatformAdministrator();

        User targetUser = findUser(userId);

        PlatformRoleName requestedRole =
                request.getRole();

        if (requestedRole == null) {
            throw new IllegalArgumentException(
                    "User role is required"
            );
        }

        boolean removingPlatformAdmin =
                hasPlatformAdminRole(targetUser)
                        && PlatformRoleName.USER.equals(
                        requestedRole
                );

        if (removingPlatformAdmin
                && Objects.equals(
                currentUser.getId(),
                targetUser.getId()
        )) {
            throw new AccessDeniedException(
                    "You cannot remove your own "
                            + "platform administrator role"
            );
        }

        if (removingPlatformAdmin) {
            validateAnotherPlatformAdministratorExists();
        }

        if (PlatformRoleName.SUPER_ADMIN.equals(
                requestedRole
        )) {
            validateEligiblePlatformAdministrator(
                    targetUser
            );
        }

        Role role =
                roleRepository
                        .findByName(requestedRole.name())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Role not found: "
                                                + requestedRole.name()
                                )
                        );

        targetUser.setRole(role);

        User savedUser =
                userRepository.saveAndFlush(
                        targetUser
                );

        return userMapper.toResponse(
                savedUser,
                getOrganizationAdminUserIds()
                        .contains(savedUser.getId())
        );
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User currentUser = requirePlatformAdministrator();
        User targetUser = findUser(userId);

        if (Objects.equals(currentUser.getId(), targetUser.getId())) {
            throw new AccessDeniedException("You cannot delete your own account");
        }

        validateLastPlatformAdministrator(targetUser, UserStatus.INACTIVE);
        targetUser.setStatus(UserStatus.INACTIVE);
        targetUser.setDeletedAt(java.time.LocalDateTime.now());
        userRepository.saveAndFlush(targetUser);
        refreshTokenService.revokeAllUserTokens(targetUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserStatsResponse getStats() {

        requirePlatformAdministrator();

        return AdminUserStatsResponse.builder()
                .totalUsers(userRepository.count())
                .activeUsers(
                        userRepository.countByStatus(
                                UserStatus.ACTIVE
                        )
                )
                .inactiveUsers(
                        userRepository.countByStatus(
                                UserStatus.INACTIVE
                        )
                )
                .bannedUsers(
                        userRepository.countByStatus(
                                UserStatus.BANNED
                        )
                )
                .platformAdmins(
                        userRepository.countByRoleNameIn(
                                PLATFORM_ADMIN_ROLES
                        )
                )
                .organizationAdmins(
                        memberRepository
                                .countDistinctAdminUsers(
                                        OrganizationMemberStatus.ACTIVE,
                                        ORGANIZATION_ADMIN_ROLES
                                )
                )
                .build();
    }

    private User requirePlatformAdministrator() {

        User currentUser =
                currentUserService.requireCurrentUser();

        if (!currentUserService.hasPlatformAdminAccess(
                currentUser
        )) {
            throw new AccessDeniedException(
                    "Platform administrator access is required"
            );
        }

        return currentUser;
    }

    private User findUser(Long userId) {

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "User id must be positive"
            );
        }

        return userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found"
                        )
                );
    }

    private Set<Long> getOrganizationAdminUserIds() {

        return memberRepository.findDistinctAdminUserIds(
                OrganizationMemberStatus.ACTIVE,
                ORGANIZATION_ADMIN_ROLES
        );
    }

    private void validateLastPlatformAdministrator(
            User targetUser,
            UserStatus requestedStatus
    ) {
        if (!hasPlatformAdminRole(targetUser)
                || !UserStatus.ACTIVE.equals(
                targetUser.getStatus()
        )
                || UserStatus.ACTIVE.equals(
                requestedStatus
        )) {
            return;
        }

        long activePlatformAdmins =
                userRepository
                        .countByStatusAndRoleNameIn(
                                UserStatus.ACTIVE,
                                PLATFORM_ADMIN_ROLES
                        );

        if (activePlatformAdmins <= 1) {
            throw new IllegalArgumentException(
                    "The last active platform administrator "
                            + "cannot be deactivated or banned"
            );
        }
    }

    private void validateAnotherPlatformAdministratorExists() {

        long activePlatformAdmins =
                userRepository
                        .countByStatusAndRoleNameIn(
                                UserStatus.ACTIVE,
                                PLATFORM_ADMIN_ROLES
                        );

        if (activePlatformAdmins <= 1) {
            throw new IllegalArgumentException(
                    "The last active platform administrator "
                            + "cannot be demoted"
            );
        }
    }

    private void validateEligiblePlatformAdministrator(
            User user
    ) {
        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new IllegalArgumentException(
                    "Platform administrator must be active"
            );
        }

        if (!Boolean.TRUE.equals(
                user.getEmailVerified()
        )) {
            throw new IllegalArgumentException(
                    "Platform administrator email "
                            + "must be verified"
            );
        }
    }

    private boolean hasPlatformAdminRole(User user) {

        return user.getRole() != null
                && user.getRole().getName() != null
                && PLATFORM_ADMIN_ROLES.contains(
                user.getRole()
                        .getName()
                        .trim()
                        .toUpperCase(Locale.ROOT)
        );
    }
    private String normalizeOptionalValue(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private void validatePagination(
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number must not be negative"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and "
                            + MAX_PAGE_SIZE
            );
        }
    }

    private String resolveSortField(
            String sortBy
    ) {
        if (sortBy == null || sortBy.isBlank()) {
            throw new IllegalArgumentException(
                    "Sort field must not be blank"
            );
        }

        String requestedField = sortBy.trim();

        return ALLOWED_SORT_FIELDS
                .stream()
                .filter(field ->
                        field.equalsIgnoreCase(
                                requestedField
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unsupported user sort field"
                        )
                );
    }

    private Sort.Direction resolveSortDirection(
            String sortDir
    ) {
        if (sortDir == null || sortDir.isBlank()) {
            throw new IllegalArgumentException(
                    "Sort direction must not be blank"
            );
        }

        return switch (
                sortDir.trim()
                        .toLowerCase(Locale.ROOT)
                ) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException(
                    "Sort direction must be asc or desc"
            );
        };
    }
}