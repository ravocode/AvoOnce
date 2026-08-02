package io.github.ravocode.avoonce.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for selective idempotency protection on Spring MVC controllers or handler methods.
 *
 * <p>Only controller classes or methods annotated with {@code @Idempotent} will be processed
 * by the AvoOnce idempotency filter. Unannotated endpoints bypass idempotency checks and response
 * caching entirely.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
}
