package com.pratikdairy.product;

import com.pratikdairy.parent.configuration.AuditConfig;
import com.pratikdairy.parent.exception.GlobalExceptionHandler;
import com.pratikdairy.parent.security.InternalApiKeyFilter;
import com.pratikdairy.parent.security.JwtAuthFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableFeignClients
@EnableJpaAuditing
@ComponentScan(basePackages = "com.pratikdairy.product")
@Import({AuditConfig.class, JwtAuthFilter.class, InternalApiKeyFilter.class, GlobalExceptionHandler.class})
public class PratikDairyProductApplication {

	public static void main(String[] args) {
		SpringApplication.run(PratikDairyProductApplication.class, args);
	}

}
