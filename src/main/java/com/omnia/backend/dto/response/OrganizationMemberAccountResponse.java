package com.omnia.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMemberAccountResponse {

    private boolean confirmationRequired;

    private boolean accountCreated;

    private boolean verificationEmailSent;

    private String message;

    private OrganizationMemberCandidateResponse existingUser;

    private OrganizationMemberResponse member;
}