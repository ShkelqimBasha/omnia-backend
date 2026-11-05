package com.omnia.backend.specification;

import com.omnia.backend.entity.User;
import com.omnia.backend.enums.UserStatus;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserSpecificationCoverage35Test {
    Root<User> root; CriteriaBuilder cb; CriteriaQuery<?> query; Predicate predicate;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        root=mock(Root.class,RETURNS_DEEP_STUBS); cb=mock(CriteriaBuilder.class);query=mock(CriteriaQuery.class);predicate=mock(Predicate.class);
        when(cb.conjunction()).thenReturn(predicate);when(cb.and(any(Predicate.class),any(Predicate.class))).thenReturn(predicate);
        when(cb.or(any(Predicate[].class))).thenReturn(predicate);
        when(cb.like(any(),anyString(),anyChar())).thenReturn(predicate);
        when(cb.equal(any(),any(Object.class))).thenReturn(predicate);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"  "})
    void emptyFiltersDoNotRestrictResults(String value) {
        assertNotNull(UserSpecification.filterUsers(value,null,value).toPredicate(root,query,cb));
        verify(cb,times(3)).conjunction();verify(cb,never()).like(any(),anyString(),anyChar());
    }
    @Test void keywordIsNormalizedAndSqlWildcardsAreLiteral() {
        UserSpecification.filterUsers("  A%_\\B  ",null,null).toPredicate(root,query,cb);
        verify(cb,times(5)).like(any(),eq("%a\\%\\_\\\\b%"),eq('\\'));
        for(String field:new String[]{"firstName","lastName","username","email","phone"})verify(root).get(field);
    }
    @ParameterizedTest @EnumSource(UserStatus.class)
    void statusFilterUsesExactRequestedStatus(UserStatus status) {
        UserSpecification.filterUsers(null,status,null).toPredicate(root,query,cb);
        verify(cb).equal(root.get("status"),status);
    }
    @Test void legacyAdminFilterIncludesSuperAdministrators() {
        UserSpecification.filterUsers(null,null," admin ").toPredicate(root,query,cb);
        verify(root.get("role").get("name")).in("ADMIN","SUPER_ADMIN");
    }
    @ParameterizedTest @ValueSource(strings={" user ","super_admin"})
    void otherRolesUseNormalizedExactMatch(String role) {
        UserSpecification.filterUsers(null,null,role).toPredicate(root,query,cb);
        verify(cb).equal(any(),eq(role.trim().toUpperCase(java.util.Locale.ROOT)));
        verify(cb).upper(root.get("role").get("name"));
    }
}
