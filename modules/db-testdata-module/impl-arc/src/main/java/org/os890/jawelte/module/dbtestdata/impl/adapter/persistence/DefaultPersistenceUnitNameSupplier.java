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
package org.os890.jawelte.module.dbtestdata.impl.adapter.persistence;

import jakarta.enterprise.context.ApplicationScoped;

import org.os890.jawelte.module.dbtestdata.api.port.PersistenceUnitNameSupplier;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * Default {@link PersistenceUnitNameSupplier}: an
 * {@code @ApplicationScoped} CDI bean whose {@link #get()} delegates
 * to {@link CapturedPersistenceUnitNameHolder}. The holder is
 * populated by {@code DbTestDataArcContextContributor} during
 * cdi-module's {@code beforeAll}, before {@code BeanProcessor.process()}
 * runs.
 *
 * <p>Consumers can replace this default by providing their own
 * {@code @Alternative @Priority(N)} bean of type
 * {@link PersistenceUnitNameSupplier}.
 *
 * <p>The previous OWB / Weld path captured the value on a CDI
 * Extension instance and read it back via
 * {@code BeanManager.getExtension(...)}. ArC does not support
 * {@code BeanManager.getExtension(...)} (it throws
 * {@code UnsupportedOperationException}), so the value flow is now:
 * contributor → static holder → bean.
 *
 * <p>Under {@code @QuarkusTest} the standalone ArC bootstrap path is
 * not taken — Quarkus drives its own container init and jawelte's
 * {@code ArcContextContributor} chain never runs. The supplier
 * therefore falls back to reading the active test class via the JVM
 * system property
 * {@code org.os890.jawelte.cdi.bridge.current-test-class} (published
 * by {@code DelegatingJUnitExtension.beforeAll}) and resolves
 * {@link PersistenceConfig#persistenceUnitName()} reflectively when
 * the holder is empty.
 */
@ApplicationScoped
public class DefaultPersistenceUnitNameSupplier implements PersistenceUnitNameSupplier {

    private static final String CURRENT_TEST_CLASS_PROPERTY =
            "org.os890.jawelte.cdi.bridge.current-test-class";

    /** No-arg constructor required by the CDI normal-scope proxy. */
    public DefaultPersistenceUnitNameSupplier() {
    }

    @Override
    public String get() {
        String captured = CapturedPersistenceUnitNameHolder.get();
        if (!captured.isEmpty()) {
            return captured;
        }
        return readFromActiveTestClass();
    }

    private static String readFromActiveTestClass() {
        String testClassName = System.getProperty(CURRENT_TEST_CLASS_PROPERTY);
        if (testClassName == null || testClassName.isEmpty()) {
            return "";
        }
        try {
            Class<?> testClass = Class.forName(testClassName, false,
                    Thread.currentThread().getContextClassLoader());
            PersistenceConfig config = testClass.getAnnotation(PersistenceConfig.class);
            return config == null ? "" : config.persistenceUnitName();
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return "";
        }
    }
}
