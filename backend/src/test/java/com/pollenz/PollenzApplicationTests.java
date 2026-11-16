package com.pollenz;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pollenz.scheduling.enabled=false")
class PollenzApplicationTests {

	@Test
	void contextLoads() {
	}

}
