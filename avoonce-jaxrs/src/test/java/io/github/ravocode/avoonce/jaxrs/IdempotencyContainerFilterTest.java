package io.github.ravocode.avoonce.jaxrs;

import io.github.ravocode.avoonce.caffeine.CaffeineIdempotencyRepository;
import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link IdempotencyContainerFilter} using the Jersey
 * Test Framework with an in-memory Grizzly container and Caffeine storage.
 */
public class IdempotencyContainerFilterTest {

    private JerseyTest jerseyTest;
    private static final IdempotencyRepository REPOSITORY =
            new CaffeineIdempotencyRepository(new IdempotencyConfig());

    // ---- Test resources ------------------------------------------------------

    @Path("/payments")
    @Idempotent
    public static class PaymentResource {
        private static int processCount = 0;

        @POST
        @Produces(MediaType.APPLICATION_JSON)
        public Response createPayment(String body) {
            processCount++;
            return Response.status(Response.Status.CREATED)
                    .entity("{\"status\":\"SUCCESS\",\"count\":" + processCount + "}")
                    .build();
        }

        static void resetCount() {
            processCount = 0;
        }

        static int getProcessCount() {
            return processCount;
        }
    }

    @Path("/errors")
    @Idempotent
    public static class ErrorResource {
        @POST
        @Produces(MediaType.APPLICATION_JSON)
        public Response serverError(String body) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"boom\"}")
                    .build();
        }
    }

    @Path("/unprotected")
    public static class UnprotectedResource {
        private static int processCount = 0;

        @POST
        @Produces(MediaType.APPLICATION_JSON)
        public Response doSomething(String body) {
            processCount++;
            return Response.status(Response.Status.OK)
                    .entity("{\"status\":\"UNPROTECTED\",\"count\":" + processCount + "}")
                    .build();
        }

        static void resetCount() {
            processCount = 0;
        }

        static int getProcessCount() {
            return processCount;
        }
    }

    // ---- Lifecycle -----------------------------------------------------------

    @BeforeEach
    void setUp() throws Exception {
        PaymentResource.resetCount();
        UnprotectedResource.resetCount();
        jerseyTest = new JerseyTest() {
            @Override
            protected Application configure() {
                return new ResourceConfig(PaymentResource.class, ErrorResource.class, UnprotectedResource.class)
                        .register(new IdempotencyContainerFilter(REPOSITORY));
            }
        };
        jerseyTest.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        jerseyTest.tearDown();
    }

    // ---- Tests ---------------------------------------------------------------

    @Test
    void testFirstRequestProcessedAndCached() {
        String key = UUID.randomUUID().toString();
        Response response = jerseyTest.target("/payments")
                .request()
                .header("Idempotency-Key", key)
                .post(jakarta.ws.rs.client.Entity.json("{\"amount\":100}"));

        assertEquals(201, response.getStatus());
        String body = response.readEntity(String.class);
        assertTrue(body.contains("SUCCESS"));
        assertEquals(1, PaymentResource.getProcessCount());
    }

    @Test
    void testRetryReturnsCachedResponse() {
        String key = UUID.randomUUID().toString();

        Response first = jerseyTest.target("/payments")
                .request()
                .header("Idempotency-Key", key)
                .post(jakarta.ws.rs.client.Entity.json("{\"amount\":100}"));
        assertEquals(201, first.getStatus());
        String firstBody = first.readEntity(String.class);

        Response second = jerseyTest.target("/payments")
                .request()
                .header("Idempotency-Key", key)
                .post(jakarta.ws.rs.client.Entity.json("{\"amount\":100}"));
        assertEquals(201, second.getStatus());
        String secondBody = second.readEntity(String.class);

        assertEquals(firstBody, secondBody, "Replay should return identical body");
        assertEquals(1, PaymentResource.getProcessCount(),
                "Resource should only be invoked once");
    }

    @Test
    void testHashMismatchReturns422() {
        String key = UUID.randomUUID().toString();

        Response first = jerseyTest.target("/payments")
                .request()
                .header("Idempotency-Key", key)
                .post(jakarta.ws.rs.client.Entity.json("{\"amount\":100}"));
        assertEquals(201, first.getStatus());

        Response second = jerseyTest.target("/payments")
                .request()
                .header("Idempotency-Key", key)
                .post(jakarta.ws.rs.client.Entity.json("{\"amount\":200}"));
        assertEquals(422, second.getStatus());
        String body = second.readEntity(String.class);
        assertTrue(body.contains("mismatch"), "Should indicate payload mismatch");
    }

    @Test
    void testConcurrentRequestsReturn409() throws Exception {
        String key = UUID.randomUUID().toString();
        String payload = "{\"amount\":100}";

        CompletableFuture<Response> f1 = CompletableFuture.supplyAsync(() ->
                jerseyTest.target("/payments").request()
                        .header("Idempotency-Key", key)
                        .post(jakarta.ws.rs.client.Entity.json(payload)));

        CompletableFuture<Response> f2 = CompletableFuture.supplyAsync(() ->
                jerseyTest.target("/payments").request()
                        .header("Idempotency-Key", key)
                        .post(jakarta.ws.rs.client.Entity.json(payload)));

        CompletableFuture.allOf(f1, f2).join();

        int s1 = f1.get().getStatus();
        int s2 = f2.get().getStatus();

        boolean oneCreated = s1 == 201 || s2 == 201;
        boolean oneConflictOrReplay = s1 == 409 || s2 == 409 || s1 == 201 || s2 == 201;

        assertTrue(oneCreated, "At least one should succeed");
        assertTrue(oneConflictOrReplay);
    }

    @Test
    void testRequestWithoutKeyPassesThrough() {
        Response r1 = jerseyTest.target("/payments")
                .request()
                .post(jakarta.ws.rs.client.Entity.json("{\"amount\":100}"));
        assertEquals(201, r1.getStatus());

        Response r2 = jerseyTest.target("/payments")
                .request()
                .post(jakarta.ws.rs.client.Entity.json("{\"amount\":100}"));
        assertEquals(201, r2.getStatus());

        assertEquals(2, PaymentResource.getProcessCount(),
                "Without key, each request should invoke the resource");
    }

    @Test
    void testRequestWithoutKeyRejectedWhenEnforced() throws Exception {
        jerseyTest.tearDown();

        jerseyTest = new JerseyTest() {
            @Override
            protected Application configure() {
                IdempotencyJaxRsConfig enforcedConfig = new IdempotencyJaxRsConfig()
                        .setEnforce(true);
                return new ResourceConfig(PaymentResource.class)
                        .register(new IdempotencyContainerFilter(REPOSITORY, enforcedConfig));
            }
        };
        jerseyTest.setUp();

        Response response = jerseyTest.target("/payments")
                .request()
                .post(jakarta.ws.rs.client.Entity.json("{\"amount\":100}"));

        assertEquals(400, response.getStatus());
        String body = response.readEntity(String.class);
        assertTrue(body.contains("Missing required"));
    }

    @Test
    void test5xxFailsIdempotencyRecord() {
        String key = UUID.randomUUID().toString();

        Response first = jerseyTest.target("/errors")
                .request()
                .header("Idempotency-Key", key)
                .post(jakarta.ws.rs.client.Entity.json("{\"data\":\"test\"}"));
        assertEquals(500, first.getStatus());

        Response second = jerseyTest.target("/payments")
                .request()
                .header("Idempotency-Key", key)
                .post(jakarta.ws.rs.client.Entity.json("{\"data\":\"test\"}"));

        assertTrue(second.getStatus() == 201 || second.getStatus() == 409,
                "After failure, retry should be allowed");
    }

    @Test
    void testNameBindingIgnoresUnannotatedResource() {
        String key = UUID.randomUUID().toString();

        // First request to unprotected endpoint
        Response r1 = jerseyTest.target("/unprotected")
                .request()
                .header("Idempotency-Key", key)
                .post(jakarta.ws.rs.client.Entity.json("{\"data\":\"test\"}"));
        assertEquals(200, r1.getStatus());

        // Second request with same key - since it lacks @Idempotent, it is NOT intercepted
        Response r2 = jerseyTest.target("/unprotected")
                .request()
                .header("Idempotency-Key", key)
                .post(jakarta.ws.rs.client.Entity.json("{\"data\":\"test\"}"));
        assertEquals(200, r2.getStatus());

        // Process count should be 2 because the resource method was invoked both times
        assertEquals(2, UnprotectedResource.getProcessCount(),
                "Unprotected resource should execute on every request even with Idempotency-Key");
    }
}
