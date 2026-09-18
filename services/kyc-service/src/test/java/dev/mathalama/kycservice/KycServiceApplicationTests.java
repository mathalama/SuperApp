package dev.mathalama.kycservice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires running infrastructure (PostgreSQL, Kafka)")
@SpringBootTest
class KycServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
