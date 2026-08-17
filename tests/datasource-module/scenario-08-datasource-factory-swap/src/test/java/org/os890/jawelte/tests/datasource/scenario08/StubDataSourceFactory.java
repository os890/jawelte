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

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import jakarta.annotation.Priority;
import jakarta.annotation.sql.DataSourceDefinition;

import org.os890.jawelte.module.datasource.api.port.DataSourceFactory;

/**
 * A consumer-supplied {@link DataSourceFactory}, standing in for the
 * real reason the port exists: a factory that hands out pooled or
 * container-managed data sources instead of raw vendor objects.
 *
 * <p>It ignores {@code className} entirely — which is the point. A
 * replacement factory is free to decide what a declaration means, and
 * the declared class name here (H2's) is deliberately not what comes
 * back, so "the swap took effect" cannot be confused with "the default
 * happened to work".
 *
 * <p>{@code @Priority(100)}, below the shipped default's
 * {@code Integer.MAX_VALUE}, which is how a consumer wins.
 */
@Priority(100)
public class StubDataSourceFactory implements DataSourceFactory {

    private static final List<String> SEEN_NAMES = new ArrayList<>();

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public StubDataSourceFactory() {
    }

    /**
     * The definition names this factory was asked to realise.
     *
     * @return the names, in call order
     */
    public static List<String> seenNames() {
        return List.copyOf(SEEN_NAMES);
    }

    @Override
    public DataSource create(DataSourceDefinition definition) {
        SEEN_NAMES.add(definition.name());
        return new StubDataSource(definition.name(), definition.url());
    }
}
