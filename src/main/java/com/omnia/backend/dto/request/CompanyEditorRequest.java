package com.omnia.backend.dto.request;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanyEditorRequest {
    @NotBlank @Size(max=150) private String name;
    @NotBlank @Size(max=160) @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") private String slug;
    @Size(max=2000) private String description;
    @Email @Size(max=150) private String businessEmail;
    @Size(max=30) private String phone;
    @Size(max=500) private String address;
    @NotBlank @Pattern(regexp="ACTIVE|INACTIVE|SUSPENDED") private String status;
    @NotNull @Positive private Long administratorUserId;
    @Positive private Long logoFileId;
    @PositiveOrZero private Long revision;
    @DecimalMin("-90") @DecimalMax("90") private Double latitude;
    @DecimalMin("-180") @DecimalMax("180") private Double longitude;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean latitudeProvided;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean longitudeProvided;
    public void setLatitude(Double value){latitude=value;latitudeProvided=true;}
    public void setLongitude(Double value){longitude=value;longitudeProvided=true;}

}
