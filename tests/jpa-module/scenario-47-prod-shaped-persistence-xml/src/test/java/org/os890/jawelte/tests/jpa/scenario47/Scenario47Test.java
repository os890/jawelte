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
package org.os890.jawelte.tests.jpa.scenario47;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Verifies that a production-shaped {@code persistence.xml}
 * (transaction-type=JTA, {@code <jta-data-source>}, PostgreSQL
 * dialect) is overridden by jpa-module's bootstrap path:
 * jpa-module's H2 in-memory URL replaces the JTA data source,
 * RESOURCE_LOCAL replaces JTA, and the PostgreSQL dialect is
 * effectively ignored because the H2 connection drives Hibernate's
 * dialect detection. If the override didn't work, the EMF would
 * try to look up a JNDI {@code java:jboss/datasources/PostgresDS}
 * data source and fail.
 *
 * <p><b>The rule this half pins down.</b> Since #123 the override is
 * conditional: a unit naming a data source that something in the
 * deployment actually binds is given that data source
 * (scenario 73). Nothing binds
 * {@code java:jboss/datasources/PostgresDS} here — it names a
 * container resource the test does not provide — so jpa-module
 * supplies its own database, as it always has. The two scenarios are
 * the two halves of one rule: resolve when declared, override when
 * not.
 */
@EnableTestBeans
public class Scenario47Test {

    @Inject
    private PersonService personService;

    /** No-arg constructor for CDI. */
    public Scenario47Test() {
    }

    /**
     * The framework override is in effect: persist + query work
     * against H2 even though the persistence.xml is JTA / PG-shaped.
     */
    @Test
    public void productionShapedPersistenceXmlIsOverridden() {
        Long id = personService.persistPerson("alice");
        assertThat(id).isNotNull();
        assertThat(personService.countPeople()).isEqualTo(1L);
    }
}
