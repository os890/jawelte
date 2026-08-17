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
package org.os890.jawelte.tests.datasource.scenario03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * datasource-module is opt-in, and being on the classpath is not the
 * same as being used. This scenario has the module's api and impl jars
 * present — the aggregator puts them on every scenario's classpath —
 * and declares no {@code @DataSourceDefinition} anywhere.
 *
 * <p>Nothing may happen as a result: the container boots as it always
 * did, no {@code DataSource} bean exists, and the naming tree is
 * untouched. This is the regression guard for the claim that adding
 * the module to a project changes nothing until a definition is
 * actually written.
 */
@EnableTestBeans
class Scenario03Test {

    @Inject
    OrdinaryBean ordinaryBean;

    @Test
    void theContainerBootsAndOrdinaryBeansStillWork() {
        assertThat(ordinaryBean).isNotNull();
        assertThat(ordinaryBean.greet()).isEqualTo("hello");
    }

    @Test
    void noDataSourceBeanIsRegistered() {
        assertThat(CDI.current().select(DataSource.class).isUnsatisfied())
                .as("without a @DataSourceDefinition the extension must register no synthetic bean")
                .isTrue();
    }

    @Test
    void nothingIsBoundInJndi() throws NamingException {
        InitialContext context = new InitialContext();

        assertThatThrownBy(() -> context.lookup("java:comp/env/jdbc/OrdersDS"))
                .as("the lifecycle adapter must not bind anything when nothing was declared")
                .isInstanceOf(NamingException.class);
    }
}
