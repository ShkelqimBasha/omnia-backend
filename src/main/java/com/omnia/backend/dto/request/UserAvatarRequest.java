package com.omnia.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class UserAvatarRequest {

    @NotNull(
            message = "Avatar file id must not be null"
    )
    @Positive(
            message = "Avatar file id must be positive"
    )
    private Long uploadedFileId;
}