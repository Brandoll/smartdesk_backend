package com.smartdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SmartdeskApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartdeskApplication.class, args);
	}

}
