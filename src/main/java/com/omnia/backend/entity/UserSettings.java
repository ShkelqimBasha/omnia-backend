package com.omnia.backend.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="user_settings") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSettings {
    @Id @Column(name="user_id") private Long userId;
    @Column(name="full_name",length=200) private String fullName;
    @Column(length=30) private String phone;
    @Column(length=500) private String address;
    @Column(name="theme_mode",nullable=false,length=10) private String theme;
    @Column(name="language_code",nullable=false,length=2) private String language;
    @Column(name="currency_code",nullable=false,length=3) private String currency;
    @Column(name="notifications_enabled",nullable=false) private boolean notifications;
    @Column(name="accent_color",nullable=false,length=16) private String accentColor;
    @Version private Long revision;
}
