package com.pollenz;

import org.springframework.boot.SpringApplication;

public class TestPollenzApplication {

	public static void main(String[] args) {
		SpringApplication.from(PollenzApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
