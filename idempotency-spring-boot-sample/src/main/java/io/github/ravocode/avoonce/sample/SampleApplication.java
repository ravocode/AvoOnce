package io.github.ravocode.avoonce.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the sample application.
 */
@SpringBootApplication
public class SampleApplication {

    /**
     * Starts the sample application.
     *
     * @param args the command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    /*
     * -------------------------------------------------------------------
     * Redis Configuration Example
     * -------------------------------------------------------------------
     * To use the Redis backend:
     * 1. Change 'avoonce.idempotency.store=redis' in application.properties
     * 2. Uncomment the JedisPool bean below (or provide a Lettuce RedisClient)
     * 3. Ensure a local Redis server is running on port 6379
     */
    // @Bean
    // public redis.clients.jedis.JedisPool jedisPool() {
    //     return new redis.clients.jedis.JedisPool("localhost", 6379);
    // }
}
