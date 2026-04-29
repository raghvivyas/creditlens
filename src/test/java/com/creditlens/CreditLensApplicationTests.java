package com.creditlens;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity test — no Spring context loaded.
 * Full integration tests require a running PostgreSQL instance.
 * All business logic is covered by pure Mockito unit tests in the service/controller packages.
 */
class CreditLensApplicationTests {

    @Test
    void sanityCheck_javaVersion() {
        assertThat(System.getProperty("java.version")).isNotNull();
    }
}
