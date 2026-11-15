package com.sevak.api.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sevak.api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The tests log in as the container's superuser, which is exactly the login production must refuse. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppRoleCheckTest extends IntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void aSuperuserLoginIsRefusedEvenThoughRequestsSwitchToTheAppRole() {
        assertThatThrownBy(() -> new AppRoleCheck(jdbcClient, transactions, false).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could switch back out of 'sevak_app'");
    }

    @Test
    void requestsRunningAsTheOwnerAreRefusedWhateverTheSetting() {
        UserContextTransactionManager asOwner =
                new UserContextTransactionManager(((JdbcTransactionManager) transactions).getDataSource(), "test");
        assertThatThrownBy(() -> new AppRoleCheck(jdbcClient, asOwner, true).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("skips row-level security");
    }

    @Test
    void theTestsDeliberateOwnerLoginIsAllowedOnlyWhenSaidSo() {
        assertThatCode(() -> new AppRoleCheck(jdbcClient, transactions, true).run(null))
                .doesNotThrowAnyException();
    }

    /** Rolled back afterwards: the check joins this test's transaction, which changed the owner. */
    @Test
    @Transactional
    void anOwnerThatCannotBypassRowLevelSecurityIsRefused() {
        jdbc.execute("CREATE ROLE ordinary_owner NOLOGIN");
        jdbc.execute("ALTER TABLE sevak.bookings OWNER TO ordinary_owner");

        assertThatThrownBy(() -> new AppRoleCheck(jdbcClient, transactions, true).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'ordinary_owner' is neither a superuser nor BYPASSRLS");
    }
}
