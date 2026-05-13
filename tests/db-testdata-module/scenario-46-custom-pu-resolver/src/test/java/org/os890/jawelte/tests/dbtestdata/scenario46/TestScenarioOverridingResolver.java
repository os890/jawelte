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
package org.os890.jawelte.tests.dbtestdata.scenario46;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.jpa.api.port.PersistenceUnitConnectionResolver;

/**
 * Test-only override of jpa-module's default
 * {@link PersistenceUnitConnectionResolver}. Hands out a fresh
 * connection to the scenario's H2 database for any persistence-unit
 * name; ships at {@code @Priority(0)} so the project-wide
 * {@code ServicePriorityResolver} prefers it over jpa-module's
 * default impl (which sits at {@code @Priority(Integer.MAX_VALUE)}).
 */
@Priority(0)
public class TestScenarioOverridingResolver implements PersistenceUnitConnectionResolver {

    /** Holds the connection the scenario hands back; the test sets / clears this between methods. */
    private static volatile Connection injectedConnection;

    public TestScenarioOverridingResolver() {
    }

    /** Test-side setter. */
    public static void setInjectedConnection(Connection connection) {
        injectedConnection = connection;
    }

    @Override
    public Connection connectionFor(String persistenceUnitName) {
        if (injectedConnection != null) {
            return injectedConnection;
        }
        try {
            return DriverManager.getConnection(
                    "jdbc:h2:mem:scenario46;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        } catch (SQLException sqlFailure) {
            throw new RuntimeException(sqlFailure);
        }
    }

    @Override
    public Connection connectionForActivePersistenceUnit() {
        return connectionFor(null);
    }
}
