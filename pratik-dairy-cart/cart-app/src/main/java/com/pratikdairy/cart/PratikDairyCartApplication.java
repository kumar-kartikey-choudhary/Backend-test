package com.pratikdairy.cart;

import com.pratikdairy.parent.configuration.AuditConfig;
import com.pratikdairy.parent.exception.GlobalExceptionHandler;
import com.pratikdairy.parent.security.JwtAuthFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.pratikdairy.product.controller")
@EnableJpaAuditing
@Import({AuditConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
public class PratikDairyCartApplication {

	public static void main(String[] args) {
		SpringApplication.run(PratikDairyCartApplication.class, args);
	}

}
