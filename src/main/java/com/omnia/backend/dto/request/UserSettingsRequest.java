package com.omnia.backend.dto.request;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSettingsRequest {
    @Size(max=200) private String fullName;
    @Size(max=30) private String phone;
    @Size(max=500) private String address;
    @NotNull @Pattern(regexp="system|light|dark") private String theme;
    @NotNull @Pattern(regexp="sq|en|it") private String language;
    @NotNull @Pattern(regexp="EUR|USD|ALL") private String currency;
    @NotNull private Boolean notifications;
    @NotNull @Min(-1) private Long revision;
}
