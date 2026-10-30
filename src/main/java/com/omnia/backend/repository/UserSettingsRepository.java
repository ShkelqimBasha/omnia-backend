package com.omnia.backend.repository;
import com.omnia.backend.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserSettingsRepository extends JpaRepository<UserSettings,Long>{}
