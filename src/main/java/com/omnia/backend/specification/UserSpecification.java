package com.omnia.backend.specification;

import com.omnia.backend.entity.User;
import com.omnia.backend.enums.UserStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Locale;

public final class UserSpecification {

    private static final char LIKE_ESCAPE_CHARACTER =
            '\\';

    private UserSpecification() {
    }

    public static Specification<User> filterUsers(
            String keyword,
            UserStatus status,
            String role
    ) {
        return Specification.allOf(
                List.of(
                        containsKeyword(keyword),
                        hasStatus(status),
                        hasRole(role)
                )
        );
    }

    private static Specification<User> containsKeyword(
            String keyword
    ) {
        return (root, query, criteriaBuilder) -> {

            if (keyword == null || keyword.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            String normalizedKeyword =
                    keyword.trim()
                            .toLowerCase(Locale.ROOT);

            String likePattern =
                    "%"
                            + escapeLikePattern(
                            normalizedKeyword
                    )
                            + "%";

            return criteriaBuilder.or(
                    criteriaBuilder.like(
                            criteriaBuilder.lower(
                                    root.get("firstName")
                            ),
                            likePattern,
                            LIKE_ESCAPE_CHARACTER
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(
                                    root.get("lastName")
                            ),
                            likePattern,
                            LIKE_ESCAPE_CHARACTER
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(
                                    root.get("username")
                            ),
                            likePattern,
                            LIKE_ESCAPE_CHARACTER
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(
                                    root.get("email")
                            ),
                            likePattern,
                            LIKE_ESCAPE_CHARACTER
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(
                                    root.get("phone")
                            ),
                            likePattern,
                            LIKE_ESCAPE_CHARACTER
                    )
            );
        };
    }

    private static Specification<User> hasStatus(
            UserStatus status
    ) {
        return (root, query, criteriaBuilder) -> {

            if (status == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("status"),
                    status
            );
        };
    }

    private static Specification<User> hasRole(
            String role
    ) {
        return (root, query, criteriaBuilder) -> {

            if (role == null || role.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            String normalizedRole =
                    role.trim()
                            .toUpperCase(Locale.ROOT);

            /*
             * ADMIN includes both the old ADMIN role
             * and the new SUPER_ADMIN role.
             */
            if ("ADMIN".equals(normalizedRole)) {
                return root.get("role")
                        .get("name")
                        .in(
                                "ADMIN",
                                "SUPER_ADMIN"
                        );
            }

            return criteriaBuilder.equal(
                    criteriaBuilder.upper(
                            root.get("role").get("name")
                    ),
                    normalizedRole
            );
        };
    }

    private static String escapeLikePattern(
            String value
    ) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}