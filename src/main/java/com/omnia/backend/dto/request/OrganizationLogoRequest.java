package com.omnia.backend.dto.request;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record OrganizationLogoRequest(@NotNull @Positive Long uploadedFileId) {}
