package com.omnia.backend.controller;

import com.omnia.backend.dto.request.OrganizationMemberRequest;
import com.omnia.backend.dto.request.OrganizationMemberUpdateRequest;
import com.omnia.backend.dto.response.OrganizationMemberResponse;
import com.omnia.backend.service.interfaces.OrganizationMemberService;
import com.omnia.backend.dto.response.OrganizationMemberCandidateResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/organizations/{organizationId}/members"
)
@Validated
@PreAuthorize("isAuthenticated()")
public class OrganizationMemberController {

    private final OrganizationMemberService memberService;

    public OrganizationMemberController(
            OrganizationMemberService memberService
    ) {
        this.memberService = memberService;
    }

    @PostMapping("/accounts")
    public ResponseEntity<
            com.omnia.backend.dto.response.OrganizationMemberAccountResponse>
    createMemberAccount(
            @PathVariable
            @Positive
            Long organizationId,

            @Valid
            @RequestBody
            com.omnia.backend.dto.request.OrganizationMemberAccountRequest
                    request
    ) {
        com.omnia.backend.dto.response.OrganizationMemberAccountResponse
                response =
                memberService.createMemberAccount(
                        organizationId,
                        request
                );

        if (response.isConfirmationRequired()) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity
                .status(
                        org.springframework.http.HttpStatus.CREATED
                )
                .body(response);
    }
    @PostMapping
    public ResponseEntity<OrganizationMemberResponse>
    addMember(
            @PathVariable
            @Positive
            Long organizationId,

            @Valid
            @RequestBody
            OrganizationMemberRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        memberService.addMember(
                                organizationId,
                                request
                        )
                );
    }

    @GetMapping
    public ResponseEntity<List<OrganizationMemberResponse>>
    getMembers(
            @PathVariable
            @Positive
            Long organizationId
    ) {
        return ResponseEntity.ok(
                memberService.getMembers(
                        organizationId
                )
        );
    }
    @GetMapping("/candidates")
    public ResponseEntity<
            List<OrganizationMemberCandidateResponse>
            > searchMemberCandidates(
            @PathVariable
            @Positive
            Long organizationId,

            @RequestParam
            @NotBlank
            @Size(min = 2, max = 100)
            String query
    ) {
        return ResponseEntity.ok(
                memberService.searchMemberCandidates(
                        organizationId,
                        query
                )
        );
    }

    @PutMapping("/{memberId}")
    public ResponseEntity<OrganizationMemberResponse>
    updateMember(
            @PathVariable
            @Positive
            Long organizationId,

            @PathVariable
            @Positive
            Long memberId,

            @Valid
            @RequestBody
            OrganizationMemberUpdateRequest request
    ) {
        return ResponseEntity.ok(
                memberService.updateMember(
                        organizationId,
                        memberId,
                        request
                )
        );
    }
}