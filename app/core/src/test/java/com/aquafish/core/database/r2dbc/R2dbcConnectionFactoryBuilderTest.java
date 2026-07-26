package com.aquafish.core.database.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aquafish.core.database.DatabaseSettings;
import org.junit.jupiter.api.Test;

class R2dbcConnectionFactoryBuilderTest {

    @Test
    void shouldSelectOnlyTheConfiguredDatabaseProtocol() {
        assertEquals(
            "mysql",
            R2dbcConnectionFactoryBuilder.protocol(DatabaseSettings.defaultMysql())
        );
        assertEquals(
            "mariadb",
            R2dbcConnectionFactoryBuilder.protocol(DatabaseSettings.defaultMariadb())
        );
        assertEquals(
            "postgresql",
            R2dbcConnectionFactoryBuilder.protocol(DatabaseSettings.defaultPostgresql())
        );
    }

    @Test
    void shouldBuildPasswordFreeDisplayUrl() {
        DatabaseSettings settings = new DatabaseSettings(
            DatabaseSettings.defaultPostgresql().type(),
            "db.example.com",
            5432,
            "aquafish",
            "site_user",
            "secret-password",
            "aq_"
        );

        assertEquals(
            "r2dbc:postgresql://db.example.com:5432/aquafish",
            R2dbcConnectionFactoryBuilder.displayUrl(settings)
        );
    }
}
