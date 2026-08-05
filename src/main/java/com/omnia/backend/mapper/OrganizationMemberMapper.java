package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.OrganizationMemberResponse;
import com.omnia.backend.entity.OrganizationMember;
import org.springframework.stereotype.Component;

@Component
public class OrganizationMemberMapper {

    public OrganizationMemberResponse toResponse(
            OrganizationMember member
    ) {
        if (member == null) {
            throw new IllegalArgumentException(
                    "Organization member must not be null"
            );
        }

        return OrganizationMemberResponse.builder()
                .id(member.getId())
                .organizationId(
                        member.getOrganization().getId()
                )
                .organizationName(
                        member.getOrganization().getName()
                )
                .userId(member.getUser().getId())
                .firstName(
                        member.getUser().getFirstName()
                )
                .lastName(
                        member.getUser().getLastName()
                )
                .username(
                        member.getUser().getUsername()
                )
                .email(member.getUser().getEmail())
                .globalRole(
                        member.getUser().getRole() == null
                                ? null
                                : member.getUser()
                                .getRole()
                                .getName()
                )
                .membershipRole(
                        member.getMembershipRole() == null
                                ? null
                                : member.getMembershipRole()
                                .name()
                )
                .status(
                        member.getStatus() == null
                                ? null
                                : member.getStatus().name()
                )
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }
}