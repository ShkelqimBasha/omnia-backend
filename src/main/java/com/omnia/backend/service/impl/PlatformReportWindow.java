package com.omnia.backend.service.impl;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** UTC calendar days, inclusive dates at the API and half-open SQL bounds. */
public record PlatformReportWindow(LocalDate start,LocalDate end,int days) {
    public static PlatformReportWindow resolve(LocalDate start,LocalDate end,int days,LocalDate today){
        if((start==null)!=(end==null))throw new IllegalArgumentException("Both start and end dates are required");
        if(start==null){if(days!=7&&days!=30&&days!=90)throw new IllegalArgumentException("Supported periods: 7, 30 or 90 days");end=today;start=end.minusDays(days-1L);}
        long length=ChronoUnit.DAYS.between(start,end)+1;
        if(length<1||length>366||end.isAfter(today))throw new IllegalArgumentException("Select 1-366 days, ending no later than today (UTC)");
        return new PlatformReportWindow(start,end,(int)length);
    }
    public LocalDate previousStart(){return start.minusDays(days);}
    public LocalDate previousEnd(){return start.minusDays(1);}
    public LocalDate exclusiveEnd(){return end.plusDays(1);}
}
