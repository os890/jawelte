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
package org.os890.jawelte.tests.resource.scenario04;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;

/** One bean, one member of the annotation each, as production code in the wild mixes them. */
@ApplicationScoped
public class MixedDeclarationRepository {

    @Resource(name = "java:app/jdbc/OrdersDS")
    private DataSource orders;

    @Resource(mappedName = "java:app/jdbc/AuditDS")
    private DataSource audit;

    /** No-arg constructor required by CDI. */
    public MixedDeclarationRepository() {
    }

    /**
     * @return the url the name-declared data source connects to
     * @throws SQLException if the connection fails
     */
    public String ordersUrl() throws SQLException {
        return urlOf(orders);
    }

    /**
     * @return the url the mappedName-declared data source connects to
     * @throws SQLException if the connection fails
     */
    public String auditUrl() throws SQLException {
        return urlOf(audit);
    }

    private static String urlOf(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        }
    }
}
