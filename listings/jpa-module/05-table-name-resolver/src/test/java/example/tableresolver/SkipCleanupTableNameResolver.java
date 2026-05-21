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
package example.tableresolver;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManagerFactory;

import org.os890.jawelte.module.jpa.api.port.TableNameResolver;

/**
 * Custom TableNameResolver impl that returns no tables — DbCleanupStrategy
 * therefore has nothing to wipe between methods. Result: data written
 * by one @Test method survives into the next.
 *
 * <p>@Priority lower than the framework default
 * (Integer.MAX_VALUE) wins the ServicePriorityResolver sort and
 * becomes the active impl.
 */
@Priority(1)
public class SkipCleanupTableNameResolver implements TableNameResolver {

    @Override
    public List<String> resolveTableNames(String persistenceUnitName,
                                          EntityManagerFactory entityManagerFactory) {
        return List.of();
    }
}
