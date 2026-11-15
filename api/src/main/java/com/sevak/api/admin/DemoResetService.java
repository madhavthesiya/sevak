package com.sevak.api.admin;

import com.sevak.api.common.DatabaseConfig;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Puts the public demo back to its starting data, using the same script as a fresh install.
 *
 * <p>Runs as the owner: the script empties every table with TRUNCATE, which the API's role may not do.
 */
@Service
public class DemoResetService {

    private static final String SEED_SCRIPT = "db/seed/R__3_seed_demo_data.sql";

    private final DataSource owner;
    private final TransactionTemplate tx;

    public DemoResetService(@Qualifier(DatabaseConfig.OWNER) DataSource owner) {
        this.owner = owner;
        this.tx = new TransactionTemplate(new JdbcTransactionManager(owner));
    }

    /** One transaction: visitors see either the old data or the fresh data, never a half-empty site. */
    public void reset() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(SEED_SCRIPT));
        populator.setSqlScriptEncoding("UTF-8");
        tx.executeWithoutResult(status -> populator.execute(owner));
    }
}
