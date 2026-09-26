package io.github.ravocode.avoonce.sample;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "avoonce.idempotency.store=jdbc",
        "spring.datasource.url=jdbc:h2:mem:idempotency_sample_test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "avoonce.idempotency.jdbc.auto-ddl=true"
})
class JdbcSampleIntegrationTest extends BaseSampleIntegrationTest {
}
