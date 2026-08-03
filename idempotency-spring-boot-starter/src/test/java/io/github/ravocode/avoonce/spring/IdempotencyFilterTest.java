package io.github.ravocode.avoonce.spring;

import io.github.ravocode.avoonce.caffeine.CaffeineIdempotencyRepository;
import io.github.ravocode.avoonce.core.IdempotencyManager;
import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.spring.annotation.Idempotent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {
        IdempotencyFilterTest.TestConfig.class,
        IdempotencyFilterTest.TestController.class
})
class IdempotencyFilterTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private IdempotencyFilter filter;

    private MockMvc mockMvc;

    @Autowired
    private TestController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters(filter)
                .build();
        controller.executionCount.set(0);
        controller.unannotatedExecutionCount.set(0);
    }

    @Test
    void testIdempotencyFilter_cachesResponse() throws Exception {
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

    @Test
    void testIdempotencyFilter_selectivelyProtectsAnnotatedEndpointOnly() throws Exception {
        String key = "key-selective-789";

        // 1. Annotated endpoint: First request executes
        mockMvc.perform(post("/test")
                        .header("Idempotency-Key", key)
                        .content("{\"data\":\"test\"}")
                        .contentType("application/json"))
                .andExpect(status().isCreated());
        assertEquals(1, controller.executionCount.get());

        // 2. Annotated endpoint: Second request returns cache
        mockMvc.perform(post("/test")
                        .header("Idempotency-Key", key)
                        .content("{\"data\":\"test\"}")
                        .contentType("application/json"))
                .andExpect(status().isCreated());
        assertEquals(1, controller.executionCount.get(), "Annotated endpoint should return cached response");

        // 3. Unannotated endpoint: First request executes
        mockMvc.perform(post("/unprotected")
                        .header("Idempotency-Key", key)
                        .content("{\"data\":\"test\"}")
                        .contentType("application/json"))
                .andExpect(status().isOk());
        assertEquals(1, controller.unannotatedExecutionCount.get());

        // 4. Unannotated endpoint: Second request executes AGAIN even with same key
        mockMvc.perform(post("/unprotected")
                        .header("Idempotency-Key", key)
                        .content("{\"data\":\"test\"}")
                        .contentType("application/json"))
                .andExpect(status().isOk());
        assertEquals(2, controller.unannotatedExecutionCount.get(), "Unannotated endpoint should bypass filter");
    }

    @Test
    void testIdempotencyFilter_rejectsBlankKey() throws Exception {
        mockMvc.perform(post("/test")
                        .header("Idempotency-Key", "   ")
                        .content("{\"data\":\"test\"}")
                        .contentType("application/json"))
                .andExpect(status().isBadRequest());
        assertEquals(0, controller.executionCount.get(), "Controller should not execute for a blank key");
    }

    @Test
    void testIdempotencyFilter_rejectsKeyExceedingMaxLength() throws Exception {
        IdempotencyProperties props = new IdempotencyProperties();
        props.setMaxKeyLength(16);
        MockMvc strictMvc = buildMvcWith(props);

        strictMvc.perform(post("/test")
                        .header("Idempotency-Key", "this-key-is-definitely-longer-than-sixteen-characters")
                        .content("{\"data\":\"test\"}")
                        .contentType("application/json"))
                .andExpect(status().isBadRequest());
        assertEquals(0, controller.executionCount.get(), "Controller should not execute for an oversized key");

        // A key within the limit passes through normally
        strictMvc.perform(post("/test")
                        .header("Idempotency-Key", "short-key-1")
                        .content("{\"data\":\"test\"}")
                        .contentType("application/json"))
                .andExpect(status().isCreated());
        assertEquals(1, controller.executionCount.get(), "A key within the limit should be accepted");
    }

    @Test
    void testIdempotencyFilter_rejectsKeyFailingPattern() throws Exception {
        IdempotencyProperties props = new IdempotencyProperties();
        props.setKeyPattern("^[a-fA-F0-9\\-]{36}$"); // UUID format
        MockMvc strictMvc = buildMvcWith(props);

        strictMvc.perform(post("/test")
                        .header("Idempotency-Key", "not-a-uuid")
                        .content("{\"data\":\"test\"}")
                        .contentType("application/json"))
                .andExpect(status().isBadRequest());
        assertEquals(0, controller.executionCount.get(), "Controller should not execute for a non-matching key");

        // A UUID-shaped key passes validation
        strictMvc.perform(post("/test")
                        .header("Idempotency-Key", "11111111-2222-3333-4444-555555555555")
                        .content("{\"data\":\"test\"}")
                        .contentType("application/json"))
                .andExpect(status().isCreated());
        assertEquals(1, controller.executionCount.get(), "A matching key should be accepted");
    }

    private MockMvc buildMvcWith(IdempotencyProperties props) {
        IdempotencyManager localManager = new IdempotencyManager(
                new CaffeineIdempotencyRepository(new IdempotencyConfig()));
        IdempotencyFilter strictFilter = new IdempotencyFilter(localManager, props,
                context.getBeanProvider(RequestMappingHandlerMapping.class));
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(strictFilter)
                .build();
    }

    @Configuration
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
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
        public IdempotencyFilter idempotencyFilter(IdempotencyManager manager,
                                                  IdempotencyProperties properties,
                                                  ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider) {
            return new IdempotencyFilter(manager, properties, handlerMappingProvider);
        }
    }

    @RestController
    static class TestController {
        final AtomicInteger executionCount = new AtomicInteger(0);
        final AtomicInteger unannotatedExecutionCount = new AtomicInteger(0);

        @PostMapping("/test")
        @Idempotent
        public ResponseEntity<String> processRequest(@RequestBody String body) {
            executionCount.incrementAndGet();
            String data = body.contains("test1") ? "test1" : (body.contains("test2") ? "test2" : "test");
            return ResponseEntity.status(201)
                    .header("X-Custom-Header", "Value")
                    .body("Response: " + data);
        }

        @PostMapping("/unprotected")
        public ResponseEntity<String> processUnprotected(@RequestBody String body) {
            unannotatedExecutionCount.incrementAndGet();
            return ResponseEntity.ok("Unprotected response");
        }
    }
}
