package com.omnia.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableMethodSecurity
public class OmniaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(OmniaBackendApplication.class, args);
	}

}
