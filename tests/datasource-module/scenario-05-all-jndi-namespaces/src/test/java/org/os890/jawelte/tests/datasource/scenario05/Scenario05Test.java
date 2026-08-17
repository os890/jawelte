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
package org.os890.jawelte.tests.datasource.scenario05;

import static org.assertj.core.api.Assertions.assertThat;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.annotation.sql.DataSourceDefinitions;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Jakarta defines four namespaces a {@code @DataSourceDefinition} name
 * may sit in, and they nest to different depths —
 * {@code java:comp/env/jdbc/X} is four segments,
 * {@code java:global/X} is two. The binder creates the intermediate
 * contexts a name implies, so this scenario is what keeps that from
 * being accidentally specific to the one prefix the other scenarios
 * happen to use.
 */
@EnableTestBeans
@DataSourceDefinitions({
        @DataSourceDefinition(
                name = "java:comp/env/jdbc/CompDS",
                className = "org.h2.jdbcx.JdbcDataSource",
                url = "jdbc:h2:mem:scenario05_comp;DB_CLOSE_DELAY=-1",
                user = "sa", password = ""),
        @DataSourceDefinition(
                name = "java:module/jdbc/ModuleDS",
                className = "org.h2.jdbcx.JdbcDataSource",
                url = "jdbc:h2:mem:scenario05_module;DB_CLOSE_DELAY=-1",
                user = "sa", password = ""),
        @DataSourceDefinition(
                name = "java:app/jdbc/AppDS",
                className = "org.h2.jdbcx.JdbcDataSource",
                url = "jdbc:h2:mem:scenario05_app;DB_CLOSE_DELAY=-1",
                user = "sa", password = ""),
        @DataSourceDefinition(
                name = "java:global/GlobalDS",
                className = "org.h2.jdbcx.JdbcDataSource",
                url = "jdbc:h2:mem:scenario05_global;DB_CLOSE_DELAY=-1",
                user = "sa", password = "")
})
class Scenario05Test {

    @Inject
    @Named("java:comp/env/jdbc/CompDS")
    DataSource comp;

    @Inject
    @Named("java:module/jdbc/ModuleDS")
    DataSource module;

    @Inject
    @Named("java:app/jdbc/AppDS")
    DataSource app;

    @Inject
    @Named("java:global/GlobalDS")
    DataSource global;

    @ParameterizedTest
    @ValueSource(strings = {
            "java:comp/env/jdbc/CompDS",
            "java:module/jdbc/ModuleDS",
            "java:app/jdbc/AppDS",
            "java:global/GlobalDS"})
    void everyStandardNamespaceResolves(String jndiName) throws NamingException {
        Object bound = new InitialContext().lookup(jndiName);

        assertThat(bound).isInstanceOf(DataSource.class);
    }

    @Test
    void eachNamespaceHoldsItsOwnDistinctDataSource() {
        assertThat(comp).isNotNull();
        assertThat(module).isNotNull();
        assertThat(app).isNotNull();
        assertThat(global).isNotNull();

        assertThat(java.util.Set.of(
                System.identityHashCode(comp),
                System.identityHashCode(module),
                System.identityHashCode(app),
                System.identityHashCode(global)))
                .as("four declarations are four data sources, one per namespace")
                .hasSize(4);
    }

    @Test
    void aShallowGlobalNameBindsAsReadilyAsADeepOne() throws NamingException {
        Object shallow = new InitialContext().lookup("java:global/GlobalDS");

        assertThat(shallow)
                .as("java:global/GlobalDS nests one level deep where "
                        + "java:comp/env/jdbc/CompDS nests three; both must bind")
                .isSameAs(global);
    }
}
