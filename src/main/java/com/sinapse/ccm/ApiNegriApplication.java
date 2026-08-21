package com.sinapse.ccm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApiNegriApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiNegriApplication.class, args);
	}

}
