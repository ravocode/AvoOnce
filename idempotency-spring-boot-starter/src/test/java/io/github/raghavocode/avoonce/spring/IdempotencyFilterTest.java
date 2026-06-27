package io.github.raghavocode.avoonce.spring;

import io.github.raghavocode.avoonce.caffeine.CaffeineIdempotencyRepository;
import io.github.raghavocode.avoonce.core.IdempotencyManager;
import io.github.raghavocode.avoonce.core.config.IdempotencyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.context.annotation.Import;

@WebMvcTest(IdempotencyFilterTest.TestController.class)
@Import({IdempotencyFilterTest.TestConfig.class, IdempotencyFilterTest.TestController.class})
class IdempotencyFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestController controller;

    @Test
    void testIdempotencyFilter_cachesResponse() throws Exception {
        controller.executionCount.set(0);
        String idempotencyKey = "key-123";
        String requestBody = "{\"data\":\"test\"}";

        // First request - should execute and cache
        mockMvc.perform(post("/test")
                        .header("Idempotency-Key", idempotencyKey)
                        .content(requestBody)
                        .contentType("application/json"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Custom-Header", "Value"))
                .andExpect(content().string("Response: test"));

        assertEquals(1, controller.executionCount.get(), "Controller should have been executed once");

        // Second request - should hit cache
        mockMvc.perform(post("/test")
                        .header("Idempotency-Key", idempotencyKey)
                        .content(requestBody)
                        .contentType("application/json"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Custom-Header", "Value"))
                .andExpect(content().string("Response: test"));

        assertEquals(1, controller.executionCount.get(), "Controller should NOT have been executed again");
    }

    @Test
    void testIdempotencyFilter_mismatchHash() throws Exception {
        controller.executionCount.set(0);
        String idempotencyKey = "key-456";

        mockMvc.perform(post("/test")
                        .header("Idempotency-Key", idempotencyKey)
                        .content("{\"data\":\"test1\"}")
                        .contentType("application/json"))
                .andExpect(status().isCreated());

        // Same key, different body
        mockMvc.perform(post("/test")
                        .header("Idempotency-Key", idempotencyKey)
                        .content("{\"data\":\"test2\"}")
                        .contentType("application/json"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void testIdempotencyFilter_missingKey() throws Exception {
        // Since enforce is false by default, it should just pass through
        mockMvc.perform(post("/test")
                        .content("{\"data\":\"test\"}")
                        .contentType("application/json"))
                .andExpect(status().isCreated());
    }

    @Configuration
    @SpringBootApplication
    static class TestConfig {
        @Bean
        public IdempotencyProperties idempotencyProperties() {
            return new IdempotencyProperties();
        }

        @Bean
        public CaffeineIdempotencyRepository repository() {
            return new CaffeineIdempotencyRepository(new IdempotencyConfig());
        }

        @Bean
        public IdempotencyManager manager(CaffeineIdempotencyRepository repository) {
            return new IdempotencyManager(repository);
        }

        @Bean
        public IdempotencyFilter filter(IdempotencyManager manager, IdempotencyProperties properties) {
            return new IdempotencyFilter(manager, properties);
        }
    }

    @RestController
    static class TestController {
        final AtomicInteger executionCount = new AtomicInteger(0);

        @PostMapping("/test")
        public ResponseEntity<String> processRequest(@RequestBody String body) {
            executionCount.incrementAndGet();
            String data = body.contains("test1") ? "test1" : (body.contains("test2") ? "test2" : "test");
            return ResponseEntity.status(201)
                    .header("X-Custom-Header", "Value")
                    .body("Response: " + data);
        }
    }
}
