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
package org.os890.jawelte.tests.resource.scenario01;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Holds both idioms side by side: the one a Jakarta EE application
 * actually writes, and the one jawelte offered before this module
 * existed.
 */
@ApplicationScoped
public class OrderRepository {

    @Resource(lookup = "java:app/jdbc/AppDS")
    private DataSource declared;

    @Inject
    @Named("java:app/jdbc/AppDS")
    private DataSource injected;

    /** No-arg constructor required by CDI. */
    public OrderRepository() {
    }

    /**
     * @return the data source obtained the way production code obtains it
     */
    public DataSource declared() {
        return declared;
    }

    /**
     * @return the same data source obtained through CDI
     */
    public DataSource injected() {
        return injected;
    }

    /**
     * @return the url the {@code @Resource} data source connects to
     * @throws SQLException if the connection fails
     */
    public String connectedUrl() throws SQLException {
        try (Connection connection = declared.getConnection()) {
            return connection.getMetaData().getURL();
        }
    }
}
