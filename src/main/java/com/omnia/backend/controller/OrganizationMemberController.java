package com.omnia.backend.controller;

import com.omnia.backend.dto.request.OrganizationMemberRequest;
import com.omnia.backend.dto.request.OrganizationMemberUpdateRequest;
import com.omnia.backend.dto.response.OrganizationMemberResponse;
import com.omnia.backend.service.interfaces.OrganizationMemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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