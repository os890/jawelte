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
package org.os890.jawelte.module.datasource.impl.adapter.cdi;

import java.lang.reflect.Type;
import java.util.Set;

import javax.sql.DataSource;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.SyntheticBeanTypeDeclaration;
import org.os890.jawelte.module.datasource.impl.adapter.extension.DataSourceDefinitionCdiExtension;

/**
 * Tells cdi-module that {@link DataSource} is spoken for whenever a
 * {@code @DataSourceDefinition} was discovered, so auto-mocking does not
 * register a second bean of that type against the same injection point.
 *
 * <p>Without this, a plain {@code @Inject DataSource} anywhere in the
 * deployment fails the container bootstrap with
 * {@code AmbiguousResolutionException}: the synthetic beans this module
 * registers in {@code AfterBeanDiscovery} are not visible to the
 * {@code BeanManager.getBeans(...)} check auto-mock uses to decide
 * whether an injection point still needs a mock (see
 * {@link SyntheticBeanTypeDeclaration} for the measurements).
 *
 * <p><b>Conditional, deliberately.</b> With no {@code @DataSourceDefinition}
 * anywhere the declaration is empty and a {@code DataSource} injection
 * point is auto-mocked exactly as it was before this module existed —
 * having the module on the classpath must not change behaviour on its
 * own. The condition is read from the extension, which the extension
 * published on the active {@link TestContext} during
 * {@code BeforeBeanDiscovery}.
 *
 * <p>Declared by type rather than by name on purpose. A definition named
 * {@code java:app/jdbc/AppDS} and an injection point qualified
 * {@code @Named("java:app/jdbc/Typo")} should fail as an unsatisfied
 * dependency naming the mismatch, not resolve quietly to a mock.
 *
 * <p>Discovered via {@code ServiceLoader}; every provider is consulted,
 * so this one coexists with any other module's declaration.
 */
public class DataSourceSyntheticBeanTypeDeclaration implements SyntheticBeanTypeDeclaration {

    /** No-arg constructor required by SPI {@code ServiceLoader} lookup. */
    public DataSourceSyntheticBeanTypeDeclaration() {
    }

    @Override
    public Set<Type> declaredTypes(TestContext testContext) {
        if (testContext == null) {
            return Set.of();
        }
        return testContext.getMetadata(DataSourceDefinitionCdiExtension.class)
                .filter(extension -> !extension.discoveredDefinitions().isEmpty())
                .<Set<Type>>map(extension -> Set.of(DataSource.class))
                .orElseGet(Set::of);
    }
}
