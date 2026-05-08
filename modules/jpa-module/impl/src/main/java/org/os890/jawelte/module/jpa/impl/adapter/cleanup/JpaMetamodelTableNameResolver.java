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
package org.os890.jawelte.module.jpa.impl.adapter.cleanup;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;

import org.os890.jawelte.module.jpa.api.port.TableNameResolver;

/**
 * Optional metamodel-backed {@link TableNameResolver} — derives table
 * names from the JPA metamodel by reading the {@code @Table} annotation
 * on each {@code @Entity} class (falling back to the entity name when
 * the annotation is absent). Useful when consumers want cleanup
 * restricted to types JPA actually manages, OR when the schema query
 * shipped by {@code InformationSchemaTableNameResolver} doesn't fit a
 * non-H2 environment.
 *
 * <p><strong>NOT pre-registered</strong> via {@code META-INF/services}.
 * Consumers who want this impl active drop the appropriate file in
 * their own classpath at a lower numeric {@code @Priority} than
 * {@link InformationSchemaTableNameResolver}'s {@code Integer.MAX_VALUE}.
 *
 * <p>Limitations: only mapped {@code @Entity} types are considered, so
 * {@code @JoinTable}, {@code @ElementCollection}, sequence tables, and
 * trigger-populated tables are silently skipped. This is the contract
 * choice — see {@link TableNameResolver} Javadoc for why the project's
 * default went the schema-walking route instead.
 */
@Priority(Integer.MAX_VALUE - 1)
public class JpaMetamodelTableNameResolver implements TableNameResolver {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JpaMetamodelTableNameResolver() {
    }

    @Override
    public List<String> resolveTableNames(String persistenceUnitName, EntityManagerFactory entityManagerFactory) {
        List<String> tableNames = new ArrayList<>();
        for (EntityType<?> entityType : entityManagerFactory.getMetamodel().getEntities()) {
            tableNames.add(deriveTableName(entityType));
        }
        return List.copyOf(tableNames);
    }

    private static String deriveTableName(EntityType<?> entityType) {
        Class<?> javaType = entityType.getJavaType();
        if (javaType != null) {
            Table tableAnnotation = javaType.getAnnotation(Table.class);
            if (tableAnnotation != null && !tableAnnotation.name().isEmpty()) {
                return tableAnnotation.name();
            }
        }
        return entityType.getName();
    }
}
