package com.pratikdairy.order;

import com.pratikdairy.parent.configuration.AuditConfig;
import com.pratikdairy.parent.exception.GlobalExceptionHandler;
import com.pratikdairy.parent.security.InternalApiKeyFilter;
import com.pratikdairy.parent.security.JwtAuthFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableFeignClients(basePackages = {
		"com.pratikdairy.cart.controller",
		"com.pratikdairy.product.controller",
		"com.pratikdairy.order.client"
})
@EnableJpaAuditing
@EnableScheduling
@Import({AuditConfig.class, JwtAuthFilter.class, InternalApiKeyFilter.class, GlobalExceptionHandler.class})
public class PratikDairyOrderApplication {

	public static void main(String[] args) {
		SpringApplication.run(PratikDairyOrderApplication.class, args);
	}

}
