package io.apiforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 사용량 버퍼 주기 반영(ApiKeyUsageFlusher)
public class ApiForgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiForgeApplication.class, args);
	}

}
