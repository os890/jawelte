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
package org.os890.jawelte.tests.datasource.scenario04;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * The declaration lives on an application bean, not on the test class,
 * which is where production code puts it. Discovery therefore has to
 * come from the bean archive rather than from reading the test class —
 * and the result has to be indistinguishable from a test-class-level
 * declaration.
 *
 * <p>The name also uses the {@code java:app} namespace rather than
 * {@code java:comp/env}, so the binder is exercised on a second one.
 */
@EnableTestBeans
class Scenario04Test {

    @Inject
    InventoryRepository repository;

    @Inject
    DataSource injectedDirectly;

    @Test
    void theBeanGetsTheDataSourceItDeclaredItself() throws SQLException {
        repository.store(1, "widget");

        assertThat(repository.read(1)).isEqualTo("widget");
    }

    @Test
    void aDefinitionOnABeanIsAlsoInjectableFromTheTest() {
        assertThat(injectedDirectly)
                .as("a bean-declared definition is the sole one here, so it satisfies "
                        + "an unqualified injection point like any other")
                .isNotNull();
    }

    @Test
    void theBeanDeclaredDefinitionIsBoundInJndi() throws NamingException {
        Object bound = new InitialContext().lookup("java:app/jdbc/InventoryDS");

        assertThat(bound).isInstanceOf(DataSource.class);
    }
}
