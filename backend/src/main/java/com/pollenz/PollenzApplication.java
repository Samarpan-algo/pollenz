package com.pollenz;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PollenzApplication {

	public static void main(String[] args) {
		// The app works in UTC. Also avoids PostgreSQL rejecting legacy OS zone names such as "Asia/Calcutta".
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(PollenzApplication.class, args);
	}

}
