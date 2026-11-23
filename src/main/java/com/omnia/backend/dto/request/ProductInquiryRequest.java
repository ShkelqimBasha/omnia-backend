package com.omnia.backend.dto.request;

import com.omnia.backend.enums.InquiryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductInquiryRequest {

    /** CONTACT ("Kontakto") or VISIT ("Kërko vizitë"). */
    @NotNull
    private InquiryType type;

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotBlank
    @Pattern(regexp = "^[+]?[0-9 ()-]{6,25}$")
    private String phone;

    @Size(max = 1000)
    private String message;

    /** VISIT only: the day the buyer would like to visit. */
    private LocalDate preferredDate;
}
