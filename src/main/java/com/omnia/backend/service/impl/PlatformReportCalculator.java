package com.omnia.backend.service.impl;
import com.omnia.backend.dto.response.PlatformReportResponse;
import com.omnia.backend.dto.response.PlatformReportResponse.*;
import java.math.*;
import java.time.*;
import java.util.*;

public final class PlatformReportCalculator {
    private PlatformReportCalculator(){}
    public static PlatformReportResponse aggregate(PlatformReportWindow w,Long scope,String scopeName,List<OrganizationChoice> choices,
            List<PlatformReportEvent> events,long users,long previousUsers,long companies,long previousCompanies){
        BigDecimal revenue=BigDecimal.ZERO,previousRevenue=BigDecimal.ZERO;long orders=0,previousOrders=0;
        Map<LocalDate,BigDecimal> dailyRevenue=new HashMap<>();Map<LocalDate,Long> dailyOrders=new HashMap<>();
        Map<Long,BigDecimal> companyRevenue=new HashMap<>();Map<Long,Long> companyOrders=new HashMap<>();
        for(PlatformReportEvent e:events){if(scope!=null&&!scope.equals(e.organizationId()))continue;
            if(e.date().isBefore(w.previousStart())||e.date().isAfter(w.end()))continue;
            if(e.date().isBefore(w.start())){previousRevenue=previousRevenue.add(e.revenue());previousOrders+=e.orders();continue;}
            revenue=revenue.add(e.revenue());orders+=e.orders();dailyRevenue.merge(e.date(),e.revenue(),BigDecimal::add);dailyOrders.merge(e.date(),e.orders(),Long::sum);
            companyRevenue.merge(e.organizationId()==null?0L:e.organizationId(),e.revenue(),BigDecimal::add);companyOrders.merge(e.organizationId()==null?0L:e.organizationId(),e.orders(),Long::sum);
        }
        List<Day> daily=new ArrayList<>();for(LocalDate d=w.start();!d.isAfter(w.end());d=d.plusDays(1))daily.add(new Day(d,dailyRevenue.getOrDefault(d,BigDecimal.ZERO),dailyOrders.getOrDefault(d,0L)));
        Map<Long,OrganizationChoice> companyNames=new LinkedHashMap<>();for(OrganizationChoice c:choices)if(scope==null||scope.equals(c.id()))companyNames.put(c.id(),c);
        for(Long id:companyRevenue.keySet())companyNames.putIfAbsent(id,new OrganizationChoice(id,null,null));
        List<Company> performance=new ArrayList<>();for(OrganizationChoice c:companyNames.values()){
            BigDecimal amount=companyRevenue.getOrDefault(c.id(),BigDecimal.ZERO);performance.add(new Company(c.id(),c.name(),c.logoUrl(),amount,companyOrders.getOrDefault(c.id(),0L),revenue.signum()==0?BigDecimal.ZERO:amount.multiply(BigDecimal.valueOf(100)).divide(revenue,2,RoundingMode.HALF_UP)));}
        performance.sort(Comparator.comparing(Company::revenue).reversed().thenComparing(Company::id));
        return new PlatformReportResponse(1,"EUR","UTC",w.start(),w.end(),w.previousStart(),w.previousEnd(),Instant.now(),scope,scopeName,
                new Metrics(metric(revenue,previousRevenue),metric(BigDecimal.valueOf(orders),BigDecimal.valueOf(previousOrders)),metric(BigDecimal.valueOf(users),BigDecimal.valueOf(previousUsers)),metric(BigDecimal.valueOf(companies),BigDecimal.valueOf(previousCompanies))),List.copyOf(daily),List.copyOf(performance),List.copyOf(choices));
    }
    public static Metric metric(BigDecimal now,BigDecimal previous){return new Metric(now,previous,previous.signum()==0?(now.signum()==0?BigDecimal.ZERO:null):now.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous,1,RoundingMode.HALF_UP));}
}
