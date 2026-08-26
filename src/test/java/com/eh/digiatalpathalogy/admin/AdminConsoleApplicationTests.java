package com.eh.digiatalpathalogy.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.cloud.config.enabled=false",
		"kafka.topic.pathqa=path-qa-topic",
		"kafka.topic.scan-progress=scan-progress-topic",
		"kafka.topic.email=entity-email-topic"
})
class AdminConsoleApplicationTests {

	@Test
	void contextLoads() {
	}

}