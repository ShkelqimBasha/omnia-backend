package com.omnia.backend.dto.response;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record PlatformReportResponse(int version,String currency,String zone,LocalDate start,LocalDate end,
        LocalDate previousStart,LocalDate previousEnd,Instant generatedAt,Long organizationId,String organizationName,
        Metrics metrics,List<Day> daily,List<Company> companies,List<OrganizationChoice> organizations){
    public record Metric(BigDecimal current,BigDecimal previous,BigDecimal changePercent){}
    public record Metrics(Metric revenue,Metric orders,Metric users,Metric companies){}
    public record Day(LocalDate date,BigDecimal revenue,long orders){}
    public record Company(Long id,String name,String logoUrl,BigDecimal revenue,long orders,BigDecimal sharePercent){}
    public record OrganizationChoice(Long id,String name,String logoUrl){}
}
