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
package org.os890.jawelte.module.jpa.impl.adapter.entity;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;

import org.os890.jawelte.module.jpa.api.port.EntityResolver;

/**
 * Default {@link EntityResolver} shipped by jpa-module: returns
 * every {@link EntityType} from the JPA metamodel of the supplied
 * {@link EntityManagerFactory}. Consumers who want to filter
 * (e.g. exclude reference / lookup tables from cleanup) provide
 * their own impl at a lower {@code @Priority} and register it via
 * {@code META-INF/services}.
 */
@Priority(Integer.MAX_VALUE)
public class JpaMetamodelEntityResolver implements EntityResolver {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JpaMetamodelEntityResolver() {
    }

    @Override
    public List<EntityType<?>> resolveEntities(String persistenceUnitName, EntityManagerFactory entityManagerFactory) {
        return List.copyOf(entityManagerFactory.getMetamodel().getEntities());
    }
}
