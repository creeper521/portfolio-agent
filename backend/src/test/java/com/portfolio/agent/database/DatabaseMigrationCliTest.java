package com.portfolio.agent.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DatabaseMigrationCliTest {

    @Test
    void enablesBothDatabasesWithoutStartingAWebServer() {
        String[] arguments = DatabaseMigrationCli.applicationArguments();

        assertThat(Arrays.asList(arguments))
                .contains(
                        "--spring.main.web-application-type=none",
                        "--spring.main.banner-mode=off",
                        "--logging.level.root=OFF",
                        "--portfolio.database.public.enabled=true",
                        "--portfolio.database.governance.enabled=true");
    }
}
