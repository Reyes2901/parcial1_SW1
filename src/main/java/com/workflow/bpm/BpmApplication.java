package com.workflow.bpm;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.workflow.bpm.user.TestConnection;
import com.workflow.bpm.user.TestRepository;

//@SpringBootApplication(scanBasePackages = "com.workflow.bpm")

//@EnableMongoAuditing
@SpringBootApplication
public class BpmApplication {

	public static void main(String[] args) {
		SpringApplication.run(BpmApplication.class, args);
	}
	@Bean
	CommandLineRunner init(TestRepository repo) {
		return args -> {
			repo.save(new TestConnection("Mongo Atlas conectado OK prueba 714"));
			//System.out.println("Mongo conectado y escritura OK");
		};
	}
}

