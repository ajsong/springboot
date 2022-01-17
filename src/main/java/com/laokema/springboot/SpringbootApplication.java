package com.laokema.springboot;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringbootApplication {

	public static void main(String[] args) {
		//SpringApplication.run(SpringbootApplication.class, args);
		//取消启动标识
		SpringApplication application = new SpringApplication(SpringbootApplication.class);
		application.setBannerMode(Banner.Mode.OFF);
		application.run(args);
	}

}
