package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.OrganizationRequest;
import com.omnia.backend.dto.response.OrganizationResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.mapper.OrganizationMapper;
import com.omnia.backend.repository.OrganizationMemberRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.interfaces.OrganizationService;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
public class OrganizationServiceImpl
        implements OrganizationService {

    private final OrganizationRepository
            organizationRepository;

    private final OrganizationMemberRepository
            memberRepository;

    private final OrganizationMapper organizationMapper;

    private final CurrentUserService currentUserService;

    public OrganizationServiceImpl(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository memberRepository,
            OrganizationMapper organizationMapper,
            CurrentUserService currentUserService
    ) {
        this.organizationRepository =
                organizationRepository;
        this.memberRepository = memberRepository;
        this.organizationMapper = organizationMapper;
        this.currentUserService = currentUserService;
    }

    @Override
    @Transactional
    public OrganizationResponse createOrganization(
            OrganizationRequest request
    ) {
        User currentUser =
                requirePlatformAdministrator();

        String normalizedName =
                normalizeName(request.getName());

        Organization organization =
                Organization.builder()
                        .name(normalizedName)
                        .slug(createUniqueSlug(
                                normalizedName
                        ))
                        .description(
                                normalizeDescription(
                                        request.getDescription()
                                )
                        )
                        .status(
                                OrganizationStatus.ACTIVE
                        )
                        .createdBy(currentUser)
                        .build();

        Organization savedOrganization =
                organizationRepository.saveAndFlush(
                        organization
                );

        return organizationMapper.toResponse(
                savedOrganization
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponse>
    getAccessibleOrganizations() {

        User currentUser =
                currentUserService.requireCurrentUser();

        if (currentUserService.hasPlatformAdminAccess(
                currentUser
        )) {
            return organizationRepository
                    .findAll(
                            Sort.by(
                                    Sort.Direction.ASC,
                                    "name"
                            )
                    )
                    .stream()
                    .map(organizationMapper::toResponse)
                    .toList();
        }

        return memberRepository
                .findAllByUserIdAndStatusOrderByOrganizationNameAsc(
                        currentUser.getId(),
                        OrganizationMemberStatus.ACTIVE
                )
                .stream()
                .map(member ->
                        member.getOrganization()
                )
                .map(organizationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(
            Long organizationId
    ) {
        validatePositiveId(organizationId);

        User currentUser =
                currentUserService.requireCurrentUser();

        Organization organization =
                findOrganization(organizationId);

        if (!currentUserService.hasPlatformAdminAccess(
                currentUser
        )) {
            memberRepository
                    .findByOrganizationIdAndUserIdAndStatus(
                            organizationId,
                            currentUser.getId(),
                            OrganizationMemberStatus.ACTIVE
                    )
                    .orElseThrow(() ->
                            new AccessDeniedException(
                                    "You do not have access "
                                            + "to this organization"
                            )
                    );
        }

        return organizationMapper.toResponse(
                organization
        );
    }

    @Override
    @Transactional
    public OrganizationResponse updateOrganization(
            Long organizationId,
            OrganizationRequest request
    ) {
        validatePositiveId(organizationId);
        requirePlatformAdministrator();

        Organization organization =
                findOrganization(organizationId);

        organization.setName(
                normalizeName(request.getName())
        );

        organization.setDescription(
                normalizeDescription(
                        request.getDescription()
                )
        );

        /*
         * Slug remains unchanged when the company name
         * changes, so existing links remain valid.
         */
        Organization savedOrganization =
                organizationRepository.saveAndFlush(
                        organization
                );

        return organizationMapper.toResponse(
                savedOrganization
        );
    }

    @Override
    @Transactional
    public OrganizationResponse updateOrganizationStatus(
            Long organizationId,
            OrganizationStatus status
    ) {
        validatePositiveId(organizationId);
        requirePlatformAdministrator();

        if (status == null) {
            throw new IllegalArgumentException(
                    "Organization status is required"
            );
        }

        Organization organization =
                findOrganization(organizationId);

        organization.setStatus(status);

        Organization savedOrganization =
                organizationRepository.saveAndFlush(
                        organization
                );

        return organizationMapper.toResponse(
                savedOrganization
        );
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

    private String normalizeName(String name) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Organization name is required"
            );
        }

        return name.trim()
                .replaceAll("\\s+", " ");
    }

    private String normalizeDescription(
            String description
    ) {
        if (description == null
                || description.isBlank()) {
            return null;
        }

        return description.trim();
    }

    private String createUniqueSlug(String name) {

        String baseSlug = Normalizer
                .normalize(
                        name,
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");

        if (baseSlug.isBlank()) {
            baseSlug = "organization";
        }

        String candidate = baseSlug;
        int suffix = 2;

        while (organizationRepository
                .existsBySlugIgnoreCase(candidate)) {

            candidate =
                    baseSlug + "-" + suffix;

            suffix++;
        }

        return candidate;
    }

    private void validatePositiveId(
            Long organizationId
    ) {
        if (organizationId == null
                || organizationId <= 0) {
            throw new IllegalArgumentException(
                    "Organization id must be positive"
            );
        }
    }
}