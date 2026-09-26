package io.github.ravocode.avoonce.sample;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "avoonce.idempotency.store=caffeine"
})
class CaffeineSampleIntegrationTest extends BaseSampleIntegrationTest {
}
