package com.omnia.backend.dto.response;
public record UserSettingsResponse(boolean exists,String fullName,String phone,String address,
        String theme,String language,String currency,boolean notifications,long revision){}
