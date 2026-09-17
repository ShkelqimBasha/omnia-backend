package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception
        .ResourceAlreadyExistsException;
import com.omnia.backend.dto.request
        .OrganizationMemberAccountRequest;
import com.omnia.backend.dto.response
        .OrganizationMemberAccountResponse;
import com.omnia.backend.dto.response
        .OrganizationMemberResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.mapper.OrganizationMemberMapper;
import com.omnia.backend.repository.OrganizationMemberRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.security.service.OrganizationAccessService;
import com.omnia.backend.service.interfaces.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberAccountServiceImplTest {

    private static final Long ORGANIZATION_ID = 10L;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private OrganizationMemberMapper memberMapper;

    @Mock
    private OrganizationAccessService accessService;

    @Mock
    private CurrentUserService currentUserService;

    private OrganizationMemberServiceImpl memberService;

    private Organization organization;

    private User manager;

    private OrganizationMemberAccountRequest request;

    @BeforeEach
    void setUp() {
        memberService =
                new OrganizationMemberServiceImpl(
                        organizationRepository,
                        memberRepository,
                        userRepository,
                        roleRepository,
                        passwordEncoder,
                        emailVerificationService,
                        memberMapper,
                        accessService,
                        currentUserService
                );

        organization = Organization.builder()
                .id(ORGANIZATION_ID)
                .name("Omnia Store")
                .slug("omnia-store")
                .build();

        manager = User.builder()
                .id(1L)
                .username("manager")
                .email("manager@example.com")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();

        request = OrganizationMemberAccountRequest
                .builder()
                .firstName("New")
                .lastName("Member")
                .username("newmember")
                .email("NEW@example.com")
                .password("Password123!")
                .phone("0690000001")
                .membershipRole(
                        OrganizationMemberRole.STAFF
                )
                .attachExisting(false)
                .build();

        when(
                organizationRepository.findById(
                        ORGANIZATION_ID
                )
        ).thenReturn(Optional.of(organization));
    }

    @Test
    void createMemberAccount_WhenAccountIsNew_ShouldCreateUserAndMembership() {

        Role userRole = Role.builder()
                .id(3L)
                .name("USER")
                .build();

        OrganizationMemberResponse memberResponse =
                OrganizationMemberResponse.builder()
                        .id(30L)
                        .organizationId(ORGANIZATION_ID)
                        .userId(20L)
                        .build();

        when(
                userRepository.findByEmail(
                        "new@example.com"
                )
        ).thenReturn(Optional.empty());

        when(
                userRepository.findByUsername(
                        "newmember"
                )
        ).thenReturn(Optional.empty());

        when(
                roleRepository.findByName("USER")
        ).thenReturn(Optional.of(userRole));

        when(
                passwordEncoder.encode(
                        "Password123!"
                )
        ).thenReturn("encoded-password");

        when(
                userRepository.saveAndFlush(
                        any(User.class)
                )
        ).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(20L);
            return user;
        });

        when(
                currentUserService.requireCurrentUser()
        ).thenReturn(manager);

        when(
                memberRepository.saveAndFlush(
                        any(OrganizationMember.class)
                )
        ).thenAnswer(invocation -> {
            OrganizationMember member =
                    invocation.getArgument(0);
            member.setId(30L);
            return member;
        });

        when(
                memberMapper.toResponse(
                        any(OrganizationMember.class)
                )
        ).thenReturn(memberResponse);

        OrganizationMemberAccountResponse result =
                memberService.createMemberAccount(
                        ORGANIZATION_ID,
                        request
                );

        assertTrue(result.isAccountCreated());
        assertFalse(result.isConfirmationRequired());
        assertTrue(result.isVerificationEmailSent());
        assertNotNull(result.getMember());

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).saveAndFlush(
                userCaptor.capture()
        );

        User savedUser = userCaptor.getValue();

        assertEquals(
                "new@example.com",
                savedUser.getEmail()
        );
        assertEquals(
                "encoded-password",
                savedUser.getPasswordHash()
        );
        assertFalse(savedUser.getEmailVerified());

        verify(emailVerificationService)
                .createVerificationToken(savedUser);
    }

    @Test
    void createMemberAccount_WhenUserExists_ShouldRequireConfirmation() {

        User existingUser = existingUser(
                20L,
                "newmember",
                "new@example.com"
        );

        when(
                userRepository.findByEmail(
                        "new@example.com"
                )
        ).thenReturn(Optional.of(existingUser));

        when(
                userRepository.findByUsername(
                        "newmember"
                )
        ).thenReturn(Optional.of(existingUser));

        when(
                memberRepository
                        .existsByOrganizationIdAndUserId(
                                ORGANIZATION_ID,
                                20L
                        )
        ).thenReturn(false);

        OrganizationMemberAccountResponse result =
                memberService.createMemberAccount(
                        ORGANIZATION_ID,
                        request
                );

        assertTrue(result.isConfirmationRequired());
        assertFalse(result.isAccountCreated());
        assertNotNull(result.getExistingUser());
        assertEquals(
                20L,
                result.getExistingUser().getId()
        );

        verify(
                memberRepository,
                never()
        ).saveAndFlush(any());

        verify(
                emailVerificationService,
                never()
        ).createVerificationToken(any());
    }

    @Test
    void createMemberAccount_WhenConfirmed_ShouldAttachExistingUser() {

        request.setAttachExisting(true);

        User existingUser = existingUser(
                20L,
                "newmember",
                "new@example.com"
        );

        OrganizationMemberResponse memberResponse =
                OrganizationMemberResponse.builder()
                        .id(30L)
                        .organizationId(ORGANIZATION_ID)
                        .userId(20L)
                        .build();

        when(
                userRepository.findByEmail(
                        "new@example.com"
                )
        ).thenReturn(Optional.of(existingUser));

        when(
                userRepository.findByUsername(
                        "newmember"
                )
        ).thenReturn(Optional.of(existingUser));

        when(
                memberRepository
                        .existsByOrganizationIdAndUserId(
                                ORGANIZATION_ID,
                                20L
                        )
        ).thenReturn(false);

        when(
                currentUserService.requireCurrentUser()
        ).thenReturn(manager);

        when(
                memberRepository.saveAndFlush(
                        any(OrganizationMember.class)
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        when(
                memberMapper.toResponse(
                        any(OrganizationMember.class)
                )
        ).thenReturn(memberResponse);

        OrganizationMemberAccountResponse result =
                memberService.createMemberAccount(
                        ORGANIZATION_ID,
                        request
                );

        assertFalse(result.isConfirmationRequired());
        assertFalse(result.isAccountCreated());
        assertFalse(result.isVerificationEmailSent());
        assertNotNull(result.getMember());

        verify(memberRepository).saveAndFlush(
                any(OrganizationMember.class)
        );

        verify(
                userRepository,
                never()
        ).saveAndFlush(any(User.class));

        verify(
                emailVerificationService,
                never()
        ).createVerificationToken(any());
    }

    @Test
    void createMemberAccount_WhenEmailAndUsernameBelongToDifferentUsers_ShouldFail() {

        User emailUser = existingUser(
                20L,
                "emailuser",
                "new@example.com"
        );

        User usernameUser = existingUser(
                21L,
                "newmember",
                "other@example.com"
        );

        when(
                userRepository.findByEmail(
                        "new@example.com"
                )
        ).thenReturn(Optional.of(emailUser));

        when(
                userRepository.findByUsername(
                        "newmember"
                )
        ).thenReturn(Optional.of(usernameUser));

        assertThrows(
                ResourceAlreadyExistsException.class,
                () -> memberService
                        .createMemberAccount(
                                ORGANIZATION_ID,
                                request
                        )
        );

        verify(
                memberRepository,
                never()
        ).saveAndFlush(any());
    }

    private User existingUser(
            Long id,
            String username,
            String email
    ) {
        return User.builder()
                .id(id)
                .firstName("Existing")
                .lastName("User")
                .username(username)
                .email(email)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
    }
}