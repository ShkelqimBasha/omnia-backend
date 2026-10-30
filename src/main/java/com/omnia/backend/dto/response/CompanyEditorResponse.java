package com.omnia.backend.dto.response;
public record CompanyEditorResponse(Long id,String name,String slug,String description,
        String businessEmail,String phone,String address,Double latitude,Double longitude,String status,String logoUrl,Long logoFileId,
        Administrator administrator,long revision) {
    public record Administrator(Long id,String name,String email) {}
}
