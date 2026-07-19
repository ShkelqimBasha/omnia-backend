package com.omnia.backend.common.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean hasNext,
        boolean hasPrevious
) {

    public PagedResponse {
        content = List.copyOf(
                Objects.requireNonNull(
                        content,
                        "Content must not be null"
                )
        );
    }

    public static <T> PagedResponse<T> from(
            Page<T> pageData
    ) {
        Objects.requireNonNull(
                pageData,
                "Page data must not be null"
        );

        return new PagedResponse<>(
                pageData.getContent(),
                pageData.getNumber(),
                pageData.getSize(),
                pageData.getTotalElements(),
                pageData.getTotalPages(),
                pageData.isFirst(),
                pageData.isLast(),
                pageData.hasNext(),
                pageData.hasPrevious()
        );
    }
}