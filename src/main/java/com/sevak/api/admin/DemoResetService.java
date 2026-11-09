package com.sevak.api.admin;

import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Puts the public demo back to its starting data, using the same script as a fresh install. */
@Service
public class DemoResetService {

    private static final String SEED_SCRIPT = "db/seed/R__3_seed_demo_data.sql";

    private final DataSource dataSource;

    public DemoResetService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** One transaction: visitors see either the old data or the fresh data, never a half-empty site. */
    @Transactional
    public void reset() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(SEED_SCRIPT));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
    }
}
