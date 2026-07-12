package io.github.ravocode.avoonce.spring;

import io.github.ravocode.avoonce.redis.JedisRedisOperations;
import io.github.ravocode.avoonce.redis.RedisOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = { "io.github.ravocode.avoonce.redis.RedisIdempotencyRepository",
        "redis.clients.jedis.JedisPool" })
public class JedisAdapterAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(RedisOperations.class)
    @ConditionalOnBean(type = "redis.clients.jedis.JedisPool")
    public JedisRedisOperations jedisRedisOperations(redis.clients.jedis.JedisPool jedisPool) {
        return new JedisRedisOperations(jedisPool);
    }
}
