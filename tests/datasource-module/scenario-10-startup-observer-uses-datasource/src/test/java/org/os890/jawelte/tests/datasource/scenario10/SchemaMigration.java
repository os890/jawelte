/*
 * Copyright 2026 os890
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.os890.jawelte.tests.datasource.scenario10;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Stands in for Flyway / Liquibase: schema work that runs while the
 * container starts, which is where production code puts it.
 */
@ApplicationScoped
public class SchemaMigration {

    private static volatile String failure;
    private static volatile boolean ran;

    @Inject
    DataSource dataSource;

    /** No-arg constructor required by CDI. */
    public SchemaMigration() {
    }

    void onStartup(@Observes @Initialized(ApplicationScoped.class) Object event) {
        ran = true;
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS migrated (id INT PRIMARY KEY)");
            statement.execute("MERGE INTO migrated KEY(id) VALUES (1)");
        } catch (RuntimeException | SQLException startupFailure) {
            failure = startupFailure.getClass().getName() + ": " + startupFailure.getMessage();
        }
    }

    /** @return whether the startup observer was invoked at all */
    public static boolean ran() {
        return ran;
    }

    /** @return the failure the startup observer hit, or null */
    public static String failure() {
        return failure;
    }
}
