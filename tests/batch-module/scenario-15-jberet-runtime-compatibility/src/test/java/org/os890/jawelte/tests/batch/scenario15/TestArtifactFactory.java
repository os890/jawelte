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
package org.os890.jawelte.tests.batch.scenario15;

import java.util.Set;

import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.CDI;

import org.jberet.spi.ArtifactFactory;

/**
 * Minimal {@link ArtifactFactory} backed by the running CDI
 * container, resolved via {@link CDI#current()} so it is portable
 * across OpenWebBeans and Weld. Replaces JBeret's
 * {@code org.jberet.se.SEArtifactFactory}, which directly
 * references {@code org.jboss.weld.environment.se.WeldContainer}
 * and so only works under Weld.
 *
 * <p>Batch artifacts in this scenario carry
 * {@code @Named("ref") @Dependent}; JBeret hands the same
 * {@code ref} string to {@link #create(String, Class, ClassLoader)}
 * and {@link #getArtifactClass(String, ClassLoader)}, which both
 * route through {@link CDI#current()}.
 */
public class TestArtifactFactory implements ArtifactFactory {

    public TestArtifactFactory() {
    }

    @Override
    public Object create(String ref, Class<?> cls, ClassLoader classLoader) {
        return CDI.current().select(Object.class, NamedLiteral.of(ref)).get();
    }

    @Override
    public void destroy(Object instance) {
        // CDI manages @Dependent lifecycle; nothing to do here.
    }

    @Override
    public Class<?> getArtifactClass(String ref, ClassLoader classLoader) {
        Set<Bean<?>> beans = CDI.current().getBeanManager().getBeans(ref);
        if (beans.isEmpty()) {
            throw new IllegalStateException("No CDI bean named '" + ref + "' for batch artifact lookup");
        }
        return beans.iterator().next().getBeanClass();
    }
}
