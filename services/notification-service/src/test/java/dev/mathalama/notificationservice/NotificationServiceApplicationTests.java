package dev.mathalama.notificationservice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires running infrastructure (Kafka, Mail, Redis)")
@SpringBootTest
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
