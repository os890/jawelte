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
package org.os890.jawelte.tests.jpa.scenario74;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The same declaration as scenario 73, with one difference that turns
 * out to matter: credentials of its own, rather than the {@code sa} /
 * empty pair jpa-module uses for the database it generates.
 *
 * <p>An application declares real credentials, so a resolution that
 * only works when they happen to match jpa-module's defaults would
 * work in the scenario and fail for the consumer.
 */
@ApplicationScoped
@DataSourceDefinition(
        name = "java:app/jdbc/AppDS",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:scenario74;DB_CLOSE_DELAY=-1",
        user = "app",
        password = "secret")
public class AppDataSourceDeclaration {

    /** No-arg constructor required by CDI. */
    public AppDataSourceDeclaration() {
    }
}
