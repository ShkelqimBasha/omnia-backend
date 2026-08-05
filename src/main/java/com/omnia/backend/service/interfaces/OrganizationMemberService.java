package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.OrganizationMemberRequest;
import com.omnia.backend.dto.request.OrganizationMemberUpdateRequest;
import com.omnia.backend.dto.response.OrganizationMemberResponse;

import java.util.List;

public interface OrganizationMemberService {

    OrganizationMemberResponse addMember(
            Long organizationId,
            OrganizationMemberRequest request
    );

    List<OrganizationMemberResponse> getMembers(
            Long organizationId
    );

    OrganizationMemberResponse updateMember(
            Long organizationId,
            Long memberId,
            OrganizationMemberUpdateRequest request
    );
}