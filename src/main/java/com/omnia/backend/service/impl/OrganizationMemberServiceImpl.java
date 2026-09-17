package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.OrganizationMemberAccountRequest;
import com.omnia.backend.dto.request.OrganizationMemberRequest;
import com.omnia.backend.dto.request.OrganizationMemberUpdateRequest;
import com.omnia.backend.dto.response.OrganizationMemberAccountResponse;
import com.omnia.backend.dto.response.OrganizationMemberResponse;
import com.omnia.backend.dto.response.OrganizationMemberCandidateResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.mapper.OrganizationMemberMapper;
import com.omnia.backend.repository.OrganizationMemberRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.security.service.OrganizationAccessService;
import com.omnia.backend.service.interfaces.EmailVerificationService;
import com.omnia.backend.service.interfaces.OrganizationMemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Objects;

@Service
public class OrganizationMemberServiceImpl
        implements OrganizationMemberService {
    private static final int MAX_CANDIDATE_RESULTS = 20;

    private final OrganizationRepository
            organizationRepository;

    private final OrganizationMemberRepository
            memberRepository;

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    private final EmailVerificationService
            emailVerificationService;

    private final OrganizationMemberMapper memberMapper;

    private final OrganizationAccessService accessService;

    private final CurrentUserService currentUserService;

    public OrganizationMemberServiceImpl(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository memberRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            OrganizationMemberMapper memberMapper,
            OrganizationAccessService accessService,
            CurrentUserService currentUserService
    ) {
        this.organizationRepository =
                organizationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService =
                emailVerificationService;
        this.memberMapper = memberMapper;
        this.accessService = accessService;
        this.currentUserService = currentUserService;
    }

    @Override
    @Transactional
    public OrganizationMemberResponse addMember(
            Long organizationId,
            OrganizationMemberRequest request
    ) {
        validatePositiveId(
                organizationId,
                "Organization id"
        );

        accessService.requireCanManageMembers(
                organizationId
        );

        Organization organization =
                findOrganization(organizationId);

        User targetUser =
                userRepository
                        .findById(request.getUserId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "User not found"
                                )
                        );

        validateEligibleUser(targetUser);

        if (memberRepository
                .existsByOrganizationIdAndUserId(
                        organizationId,
                        targetUser.getId()
                )) {
            throw new ResourceAlreadyExistsException(
                    "User is already a member "
                            + "of this organization"
            );
        }

        User currentUser =
                currentUserService.requireCurrentUser();

        OrganizationMember member =
                OrganizationMember.builder()
                        .organization(organization)
                        .user(targetUser)
                        .membershipRole(
                                request.getMembershipRole()
                        )
                        .status(
                                OrganizationMemberStatus.ACTIVE
                        )
                        .createdBy(currentUser)
                        .build();

        OrganizationMember savedMember =
                memberRepository.saveAndFlush(member);

        return memberMapper.toResponse(savedMember);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationMemberResponse> getMembers(
            Long organizationId
    ) {
        validatePositiveId(
                organizationId,
                "Organization id"
        );

        accessService.requireCanManageMembers(
                organizationId
        );

        findOrganization(organizationId);

        return memberRepository
                .findAllByOrganizationIdOrderByIdAsc(
                        organizationId
                )
                .stream()
                .map(memberMapper::toResponse)
                .toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<OrganizationMemberCandidateResponse>
    searchMemberCandidates(
            Long organizationId,
            String query
    ) {
        validatePositiveId(
                organizationId,
                "Organization id"
        );

        accessService.requireCanManageMembers(
                organizationId
        );

        findOrganization(organizationId);

        String normalizedQuery =
                query == null
                        ? ""
                        : query.trim();

        if (normalizedQuery.length() < 2) {
            throw new IllegalArgumentException(
                                    "Search query must contain at least "
                    + "2 characters"
            );
        }

        if (normalizedQuery.length() > 100) {
            throw new IllegalArgumentException(
                    "Search query must not exceed "
                            + "100 characters"
            );
        }

        return userRepository
                .searchOrganizationMemberCandidates(
                        organizationId,
                        UserStatus.ACTIVE,
                        normalizedQuery,
                        PageRequest.of(
                                0,
                                MAX_CANDIDATE_RESULTS
                        )
                )
                .stream()
                .map(memberMapper::toCandidateResponse)
                .toList();
    }

    @Override
    @Transactional
    public OrganizationMemberResponse updateMember(
            Long organizationId,
            Long memberId,
            OrganizationMemberUpdateRequest request
    ) {
        validatePositiveId(
                organizationId,
                "Organization id"
        );

        validatePositiveId(
                memberId,
                "Member id"
        );

        accessService.requireCanManageMembers(
                organizationId
        );

        OrganizationMember member =
                findMember(
                        organizationId,
                        memberId
                );

        validateLastOwner(
                organizationId,
                member,
                request
        );

        if (OrganizationMemberStatus.ACTIVE.equals(
                request.getStatus()
        )) {
            validateEligibleUser(member.getUser());
        }

        member.setMembershipRole(
                request.getMembershipRole()
        );

        member.setStatus(request.getStatus());

        OrganizationMember savedMember =
                memberRepository.saveAndFlush(member);

        return memberMapper.toResponse(savedMember);
    }

    @Override
    @Transactional
    public OrganizationMemberAccountResponse
    createMemberAccount(
            Long organizationId,
            OrganizationMemberAccountRequest request
    ) {
        validatePositiveId(
                organizationId,
                "Organization ID"
        );

        accessService.requireCanManageMembers(
                organizationId
        );

        Organization organization =
                findOrganization(organizationId);

        String normalizedEmail =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        String normalizedUsername =
                request.getUsername().trim();

        User emailUser = userRepository
                .findByEmail(normalizedEmail)
                .orElse(null);

        User usernameUser = userRepository
                .findByUsername(normalizedUsername)
                .orElse(null);

        if (emailUser != null
                && usernameUser != null
                && !Objects.equals(
                emailUser.getId(),
                usernameUser.getId()
        )) {
            throw new ResourceAlreadyExistsException(
                    "Email and username belong "
                            + "to different user accounts"
            );
        }

        User existingUser =
                emailUser != null
                        ? emailUser
                        : usernameUser;

        if (existingUser != null) {
            validateEligibleUser(existingUser);

            if (memberRepository
                    .existsByOrganizationIdAndUserId(
                            organizationId,
                            existingUser.getId()
                    )) {
                throw new ResourceAlreadyExistsException(
                        "User is already a member "
                                + "of this organization"
                );
            }

            OrganizationMemberCandidateResponse
                    existingCandidate =
                    OrganizationMemberCandidateResponse
                            .builder()
                            .id(existingUser.getId())
                            .firstName(
                                    existingUser.getFirstName()
                            )
                            .lastName(
                                    existingUser.getLastName()
                            )
                            .username(
                                    existingUser.getUsername()
                            )
                            .email(existingUser.getEmail())
                            .build();

            if (!request.isAttachExisting()) {
                return OrganizationMemberAccountResponse
                        .builder()
                        .confirmationRequired(true)
                        .accountCreated(false)
                        .verificationEmailSent(false)
                        .message(
                                "Ekziston tashme nje llogari "
                                        + "me kete email ose username. "
                                        + "Konfirmoni lidhjen "
                                        + "me kompanine."
                        )
                        .existingUser(existingCandidate)
                        .member(null)
                        .build();
            }

            OrganizationMember savedMember =
                    saveMembership(
                            organization,
                            existingUser,
                            request.getMembershipRole()
                    );

            return OrganizationMemberAccountResponse
                    .builder()
                    .confirmationRequired(false)
                    .accountCreated(false)
                    .verificationEmailSent(false)
                    .message(
                            "Llogaria ekzistuese u lidh "
                                    + "me kompanine."
                    )
                    .existingUser(existingCandidate)
                    .member(
                            memberMapper.toResponse(
                                    savedMember
                            )
                    )
                    .build();
        }

        Role userRole = roleRepository
                .findByName("USER")
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Default role USER not found"
                        )
                );

        User newUser = User.builder()
                .firstName(
                        request.getFirstName().trim()
                )
                .lastName(
                        request.getLastName() == null
                                ? null
                                : request.getLastName().trim()
                )
                .username(normalizedUsername)
                .email(normalizedEmail)
                .passwordHash(
                        passwordEncoder.encode(
                                request.getPassword()
                        )
                )
                .phone(
                        request.getPhone() == null
                                ? null
                                : request.getPhone().trim()
                )
                .role(userRole)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();

        User savedUser =
                userRepository.saveAndFlush(newUser);

        OrganizationMember savedMember =
                saveMembership(
                        organization,
                        savedUser,
                        request.getMembershipRole()
                );

        emailVerificationService
                .createVerificationToken(savedUser);

        return OrganizationMemberAccountResponse
                .builder()
                .confirmationRequired(false)
                .accountCreated(true)
                .verificationEmailSent(true)
                .message(
                        "Llogaria dhe anetaresia u krijuan. "
                                + "Perdoruesi duhet te "
                                + "verifikoje email-in."
                )
                .existingUser(null)
                .member(
                        memberMapper.toResponse(
                                savedMember
                        )
                )
                .build();
    }

    private OrganizationMember saveMembership(
            Organization organization,
            User user,
            OrganizationMemberRole membershipRole
    ) {
        User currentUser =
                currentUserService.requireCurrentUser();

        OrganizationMember member =
                OrganizationMember.builder()
                        .organization(organization)
                        .user(user)
                        .membershipRole(membershipRole)
                        .status(
                                OrganizationMemberStatus.ACTIVE
                        )
                        .createdBy(currentUser)
                        .build();

        return memberRepository.saveAndFlush(member);
    }
    private Organization findOrganization(
            Long organizationId
    ) {
        return organizationRepository
                .findById(organizationId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Organization not found"
                        )
                );
    }

    private OrganizationMember findMember(
            Long organizationId,
            Long memberId
    ) {
        OrganizationMember member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Organization member not found"
                                )
                        );

        if (!Objects.equals(
                member.getOrganization().getId(),
                organizationId
        )) {
            throw new ResourceNotFoundException(
                    "Organization member not found"
            );
        }

        return member;
    }

    private void validateLastOwner(
            Long organizationId,
            OrganizationMember currentMember,
            OrganizationMemberUpdateRequest request
    ) {
        boolean currentlyActiveOwner =
                OrganizationMemberRole.OWNER.equals(
                        currentMember.getMembershipRole()
                )
                        && OrganizationMemberStatus.ACTIVE.equals(
                        currentMember.getStatus()
                );

        boolean remainsActiveOwner =
                OrganizationMemberRole.OWNER.equals(
                        request.getMembershipRole()
                )
                        && OrganizationMemberStatus.ACTIVE.equals(
                        request.getStatus()
                );

        if (!currentlyActiveOwner
                || remainsActiveOwner) {
            return;
        }

        long activeOwnerCount =
                memberRepository
                        .countByOrganizationIdAndMembershipRoleAndStatus(
                                organizationId,
                                OrganizationMemberRole.OWNER,
                                OrganizationMemberStatus.ACTIVE
                        );

        if (activeOwnerCount <= 1) {
            throw new IllegalArgumentException(
                    "The last active organization owner "
                            + "cannot be deactivated or demoted"
            );
        }
    }

    private void validateEligibleUser(User user) {

        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new IllegalArgumentException(
                    "Only an active user can be added "
                            + "to an organization"
            );
        }

        if (!Boolean.TRUE.equals(
                user.getEmailVerified()
        )) {
            throw new IllegalArgumentException(
                    "User email must be verified"
            );
        }
    }

    private void validatePositiveId(
            Long id,
            String fieldName
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }
}