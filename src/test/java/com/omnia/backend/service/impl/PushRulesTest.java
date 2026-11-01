package com.omnia.backend.service.impl;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class PushRulesTest {
 @Test void overnightQuietHoursUseHalfOpenBounds(){assertTrue(PushRules.quiet(true,1320,480,"UTC",Instant.parse("2026-11-01T22:00:00Z")));assertTrue(PushRules.quiet(true,1320,480,"UTC",Instant.parse("2026-11-02T07:59:00Z")));assertFalse(PushRules.quiet(true,1320,480,"UTC",Instant.parse("2026-11-02T08:00:00Z")));assertFalse(PushRules.quiet(false,1320,480,"UTC",Instant.parse("2026-11-01T23:00:00Z")));}
 @Test void daytimeAndTimeZoneBoundariesAreRespected(){assertTrue(PushRules.quiet(true,540,1020,"Europe/Tirane",Instant.parse("2026-11-01T08:00:00Z")));assertFalse(PushRules.quiet(true,540,1020,"UTC",Instant.parse("2026-11-01T08:00:00Z")));}
 @Test void invalidIntervalsAreRejected(){assertThrows(IllegalArgumentException.class,()->PushRules.validateTime(20,20,"UTC"));assertThrows(IllegalArgumentException.class,()->PushRules.validateTime(-1,500,"UTC"));assertThrows(IllegalArgumentException.class,()->PushRules.validateTime(20,1440,"UTC"));assertThrows(IllegalArgumentException.class,()->PushRules.validateTime(20,500,"Not/AZone"));}
 @Test void installationProofIsHashedAndCompared(){String secret="a".repeat(64);assertEquals(64,PushRules.hash(secret).length());assertTrue(PushRules.secretMatches(secret,PushRules.hash(secret)));assertFalse(PushRules.secretMatches("b".repeat(64),PushRules.hash(secret)));}
}
