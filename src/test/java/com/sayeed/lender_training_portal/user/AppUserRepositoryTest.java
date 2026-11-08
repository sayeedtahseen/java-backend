package com.sayeed.lender_training_portal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sayeed.lender_training_portal.support.TestcontainersConfiguration;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AppUserRepositoryTest {

    @Autowired AppUserRepository users;
    @Autowired TestEntityManager em;
    @Autowired JdbcTemplate jdbc;

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void findByEmail_findsExistingUser() {
        em.persistAndFlush(new AppUser("lena@example.com", "hash", "Lena Lender", Role.LENDER,
                Instant.parse("2026-01-01T00:00:00Z")));
        em.clear();

        assertThat(users.findByEmail("lena@example.com"))
                .get()
                .satisfies(u -> {
                    assertThat(u.getFull_name()).isEqualTo("Lena Lender");
                    assertThat(u.getRole()).isEqualTo(Role.LENDER);
                });
        assertThat(users.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void constructor_normalisesEmail_soItIsStoredLowercase() {
        em.persistAndFlush(new AppUser("Mixed.Case@Example.com", "hash", "Mixed", Role.LENDER, T0));
        em.clear();

        // Read the raw column, not the entity, to prove what is actually in the database.
        String stored = jdbc.queryForObject("select email from app_user where full_name = 'Mixed'", String.class);

        assertThat(stored).isEqualTo("mixed.case@example.com");
        assertThat(users.findByEmail("mixed.case@example.com")).isPresent();
    }

    @Test
    void rawInsertWithUppercaseEmail_isRejectedByCheckConstraint() {
        // Bypasses JPA entirely, like a manual SQL fix or another service writing to the table would.
        assertThatThrownBy(() -> jdbc.update("""
                insert into app_user (email, password_hash, full_name, role, created_at)
                values ('Upper@Example.com', 'hash', 'Upper', 'LENDER', now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_app_user_email_lower");
    }
}
