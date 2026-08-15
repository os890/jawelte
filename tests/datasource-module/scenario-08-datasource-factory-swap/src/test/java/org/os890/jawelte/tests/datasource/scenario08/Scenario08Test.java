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
package org.os890.jawelte.tests.datasource.scenario08;

import static org.assertj.core.api.Assertions.assertThat;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * The {@code DataSourceFactory} port is the module's answer to
 * "what if I need pooling / a container-managed source / my own
 * conventions". This scenario is what keeps that answer honest.
 *
 * <p>{@link StubDataSourceFactory} is registered through
 * {@code META-INF/services} at {@code @Priority(100)}, below the
 * shipped default. It ignores {@code className} and returns its own
 * type, so every assertion below fails if the default factory ran
 * instead — the declared class is H2's, and nothing here is an H2
 * object.
 */
@EnableTestBeans
@DataSourceDefinition(
        name = "java:comp/env/jdbc/SwappedDS",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:scenario08;DB_CLOSE_DELAY=-1",
        user = "sa",
        password = "")
class Scenario08Test {

    @Inject
    DataSource injected;

    @Test
    void theConsumerFactoryWinsOverTheShippedDefault() {
        assertThat(injected)
                .as("a lower @Priority than the default's Integer.MAX_VALUE wins the SPI lookup")
                .isInstanceOf(StubDataSource.class);
    }

    @Test
    void theConsumerFactoryReceivedTheDeclaration() {
        assertThat(StubDataSourceFactory.seenNames()).contains("java:comp/env/jdbc/SwappedDS");
        assertThat(((StubDataSource) injected).declaredUrl())
                .as("the whole annotation reaches the factory, not just its name")
                .isEqualTo("jdbc:h2:mem:scenario08;DB_CLOSE_DELAY=-1");
    }

    @Test
    void whatTheConsumerFactoryBuiltIsWhatGetsBound() throws NamingException {
        Object bound = new InitialContext().lookup("java:comp/env/jdbc/SwappedDS");

        assertThat(bound)
                .as("binding and injection both publish the factory's object, not a second one")
                .isSameAs(injected);
    }
}
