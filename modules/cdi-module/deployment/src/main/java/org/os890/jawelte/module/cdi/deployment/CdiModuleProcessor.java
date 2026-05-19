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
package org.os890.jawelte.module.cdi.deployment;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;

/**
 * Quarkus deployment processor for cdi-module. Closes the gaps
 * between CDI 4.0's stereotype-as-bean-defining-annotation rule and
 * Quarkus's annotated-only discovery default by feeding the
 * stereotype-annotated classes into ArC's bean archive explicitly.
 *
 * <p>The runtime artifact ({@code cdi-module/impl}) already hosts the
 * BCE that handles auto-mock / inline-field / class-level
 * {@code @TestBean} activation. This deployment artifact only
 * supplements the bean discovery — without this build step, Quarkus's
 * annotated-discovery mode skips classes whose only "bean-defining
 * annotation" is a user-defined stereotype (CDI 4.0 §4.1 says a
 * stereotype with a scope is itself bean-defining, but ArC's
 * annotated-discovery doesn't follow that chain by default for
 * stereotypes outside its known set).
 */
public class CdiModuleProcessor {

    private static final DotName CONFIG_BEAN_DOT =
            DotName.createSimple("org.os890.jawelte.core.api.ConfigBean");

    /** No-arg constructor required by Quarkus's reflective discovery. */
    public CdiModuleProcessor() {
    }

    /**
     * Add every class carrying the {@code @ConfigBean} stereotype to
     * ArC's bean archive. {@code @ConfigBean} is a CDI stereotype that
     * meta-applies {@code @ApplicationScoped}, but ArC's
     * annotated-only discovery walks bean-defining annotations on the
     * class directly — it doesn't follow the {@code @Stereotype} chain
     * unless the stereotype is registered. This build step closes the
     * gap by scanning the index for every {@code @ConfigBean}-annotated
     * class and adding it via {@link AdditionalBeanBuildItem}.
     *
     * @param combinedIndex the application's combined Jandex index
     * @return an {@link AdditionalBeanBuildItem} carrying every
     *         {@code @ConfigBean}-annotated class found in the index
     */
    @BuildStep
    public AdditionalBeanBuildItem registerConfigBeans(CombinedIndexBuildItem combinedIndex) {
        Set<String> beanClassNames = new LinkedHashSet<>();
        for (AnnotationInstance annotation : combinedIndex.getIndex().getAnnotations(CONFIG_BEAN_DOT)) {
            if (annotation.target().kind() == AnnotationTarget.Kind.CLASS) {
                beanClassNames.add(annotation.target().asClass().name().toString());
            }
        }
        if (beanClassNames.isEmpty()) {
            return AdditionalBeanBuildItem.builder().build();
        }
        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder();
        for (String beanClassName : beanClassNames) {
            builder.addBeanClass(beanClassName);
        }
        return builder.build();
    }
}
