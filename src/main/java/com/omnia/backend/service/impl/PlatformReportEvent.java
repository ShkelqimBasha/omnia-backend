package com.omnia.backend.service.impl;
import java.time.LocalDate;
import java.math.BigDecimal;
public record PlatformReportEvent(LocalDate date,Long organizationId,BigDecimal revenue,long orders) {}
