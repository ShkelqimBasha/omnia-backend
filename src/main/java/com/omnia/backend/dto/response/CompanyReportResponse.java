package com.omnia.backend.dto.response;
import java.math.BigDecimal;
import java.util.List;
public record CompanyReportResponse(Company company,PlatformReportResponse report,
        List<PlatformReportResponse.Day> previousDaily,List<TopProduct> topProducts,
        long products,long lowStock,long outOfStock,Double averageRating,long reviewCount){
    public record Company(Long id,String name,String slug,String status,String logoUrl,String description,String email,String phone,String address){}
    public record TopProduct(Long id,String name,String image,long quantity,BigDecimal value,
            BigDecimal price,BigDecimal discount,String description,Long categoryId,String categoryName,String brand,String status,int stock){}
}
