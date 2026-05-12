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
package org.os890.jawelte.tests.jpa.scenario07;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.tests.jpa.scenario07.exclude.ExcludedMarker;
import org.os890.jawelte.tests.jpa.scenario07.included.IncludedMarker;

/**
 * The MP Config key
 * {@code org.os890.jawelte.module.jpa.scan-exclude-packages}
 * (set in {@code microprofile-config.properties} at
 * {@code config_ordinal=200} so it overrides jpa-module/impl's
 * shipped defaults) replaces the exclude list with the
 * {@code …scenario07.exclude.} sub-package; that sub-package
 * is therefore dropped from {@code EntityScanner}'s auto-discovery.
 */
@EnableTestBeans
public class Scenario07Test {

    @Inject
    private EntityManagerFactory entityManagerFactory;

    /** No-arg constructor for CDI. */
    public Scenario07Test() {
    }

    /** Included entity reaches the metamodel; excluded entity is dropped. */
    @Test
    public void protectedPackagesFilterDropsTheExcludedEntity() {
        var managedEntityNames = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(entityType -> entityType.getJavaType().getName())
                .toList();

        assertThat(managedEntityNames)
                .as("entities outside the protected package must remain auto-discovered")
                .contains(IncludedMarker.class.getName());
        assertThat(managedEntityNames)
                .as("the @Entity in the protected package must be filtered out by EntityScanner")
                .doesNotContain(ExcludedMarker.class.getName());
    }
}
