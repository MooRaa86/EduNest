package com.example.gradproj.EduNest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EduNestApplication {

	public static void main(String[] args) {
		SpringApplication.run(EduNestApplication.class, args);
	}

}
