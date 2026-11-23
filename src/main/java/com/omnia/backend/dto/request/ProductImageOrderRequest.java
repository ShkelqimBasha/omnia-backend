package com.omnia.backend.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** The product's image ids in the new gallery order; the first one becomes the cover. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageOrderRequest {

    @NotEmpty(message = "Lista e fotove nuk mund të jetë bosh")
    @Size(max = 100, message = "Shumë foto në listë")
    private List<@NotNull @Positive Long> imageIds;
}
