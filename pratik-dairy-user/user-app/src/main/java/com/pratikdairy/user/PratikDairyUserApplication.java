package com.pratikdairy.user;

import com.pratikdairy.parent.configuration.AuditConfig;
import com.pratikdairy.parent.exception.GlobalExceptionHandler;
import com.pratikdairy.parent.security.InternalApiKeyFilter;
import com.pratikdairy.user.model.User;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableFeignClients
@EnableJpaAuditing
@EntityScan(basePackageClasses = User.class)
@Import({AuditConfig.class, GlobalExceptionHandler.class, InternalApiKeyFilter.class})
public class PratikDairyUserApplication {

	public static void main(String[] args) {
		SpringApplication.run(PratikDairyUserApplication.class, args);
	}

}

