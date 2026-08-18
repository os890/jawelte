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
package org.os890.jawelte.tests.resource.scenario02;

import javax.sql.DataSource;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * The wiring the ticket was filed about: a producer whose only input is
 * a {@code @Resource} field.
 *
 * <p>When the field is left null the producer returns null, and because
 * the method is {@code @ApplicationScoped} rather than
 * {@code @Dependent} the container refuses the null - so the deployment
 * comes up and the failure lands at first use, a long way from the line
 * that caused it.
 */
@ApplicationScoped
public class ReportingDataSourceProducer {

    @Resource(lookup = "java:app/jdbc/AppDS")
    private DataSource declared;

    /** No-arg constructor required by CDI. */
    public ReportingDataSourceProducer() {
    }

    /**
     * @return the declared data source, exposed under its own qualifier
     */
    @Produces
    @Reporting
    @ApplicationScoped
    public DataSource reportingDataSource() {
        return declared;
    }
}
