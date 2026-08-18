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
package org.os890.jawelte.tests.jpa.scenario73;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The application's own data source declaration, under the name its
 * {@code persistence.xml} refers to.
 *
 * <p>On a bean class rather than on the test class deliberately: that
 * is where an application puts it, and it is the case that made the
 * timing hard - a definition here is only discovered at
 * {@code ProcessAnnotatedType}, long after jpa-module used to have
 * built its factories.
 */
@ApplicationScoped
@DataSourceDefinition(
        name = "java:app/jdbc/AppDS",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:scenario73;DB_CLOSE_DELAY=-1",
        user = "sa",
        password = "")
public class AppDataSourceDeclaration {

    /** No-arg constructor required by CDI. */
    public AppDataSourceDeclaration() {
    }
}
