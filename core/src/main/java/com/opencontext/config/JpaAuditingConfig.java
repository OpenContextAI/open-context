package com.opencontext.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing configuration.
 * Enables automatic field updates for @CreatedDate, @LastModifiedDate, etc.
 * in entities with @EntityListeners(AuditingEntityListener.class).
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
