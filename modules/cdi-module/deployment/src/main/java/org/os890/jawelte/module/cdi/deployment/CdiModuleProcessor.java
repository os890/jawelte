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
import java.util.List;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
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

    private static final DotName ENABLE_TEST_BEANS_DOT =
            DotName.createSimple("org.os890.jawelte.core.api.EnableTestBeans");

    private static final DotName TEST_BEAN_DOT =
            DotName.createSimple("org.os890.jawelte.core.api.TestBean");

    private static final DotName TEST_BEANS_DOT =
            DotName.createSimple("org.os890.jawelte.core.api.TestBeans");

    /**
     * Framework-allowlist prefixes — must match
     * {@code cdi-module/impl/META-INF/microprofile-config.properties}
     * (key {@code org.os890.jawelte.module.cdi.framework-allowlist.packages}).
     * Hardcoded here because the build step runs before MP Config is
     * resolved.
     */
    private static final List<String> FRAMEWORK_ALLOWLIST_PREFIXES = List.of(
            "java.",
            "javax.",
            "jakarta.",
            "org.jboss.weld.",
            "org.apache.webbeans.",
            "org.apache.deltaspike.",
            "org.os890.jawelte.",
            // Quarkus internals — under @QuarkusTest these are
            // structurally required (test-mode interceptors, the
            // generated ComponentsProvider, etc.). Excluding them
            // breaks the build before any user-level assertion runs.
            "io.quarkus.",
            "io.smallrye.",
            "org.eclipse.microprofile.");

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
    /**
     * Honor {@code @EnableTestBeans(limitToTestBeans = true)} by
     * excluding every class not on the framework allowlist (mirrors
     * {@code FrameworkAllowlist}'s default prefixes) and not named via
     * {@code @TestBean(bean = …)} on the test class from ArC's bean
     * archive. Mirrors the standalone-ArC path's
     * {@code WhitelistFilter}-driven filtering in
     * {@code CdiTestBeanContainer.beforeAll}.
     *
     * @param combinedIndex Quarkus's combined Jandex index
     * @param producer      sink for {@link ExcludedTypeBuildItem}s
     */
    @BuildStep
    public void honorLimitToTestBeans(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<ExcludedTypeBuildItem> producer) {
        IndexView index = combinedIndex.getIndex();
        ClassInfo testClass = findLimitToTestBeansTestClass(index);
        if (testClass == null) {
            return;
        }
        Set<String> declaredAllowed = collectDeclaredAllowedTypes(testClass);
        for (ClassInfo candidate : index.getKnownClasses()) {
            String name = candidate.name().toString();
            if (name.equals(testClass.name().toString())) {
                continue;
            }
            if (declaredAllowed.contains(name)) {
                continue;
            }
            if (isFrameworkAllowed(name)) {
                continue;
            }
            producer.produce(new ExcludedTypeBuildItem(name));
        }
    }

    private static ClassInfo findLimitToTestBeansTestClass(IndexView index) {
        for (AnnotationInstance annotation : index.getAnnotations(ENABLE_TEST_BEANS_DOT)) {
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            AnnotationValue limitValue = annotation.value("limitToTestBeans");
            if (limitValue != null && limitValue.asBoolean()) {
                return annotation.target().asClass();
            }
        }
        return null;
    }

    /**
     * The classes the user has explicitly declared as bean candidates
     * via {@code @TestBean(bean=…)} (including
     * {@code @TestBeans({…})} repeatable). Producer classes from
     * {@code beanProducer=…} are intentionally NOT exempted — the
     * BCE handles them via synthetic beans, so their classes don't
     * need to be in the bean archive.
     */
    private static Set<String> collectDeclaredAllowedTypes(ClassInfo testClass) {
        Set<String> out = new LinkedHashSet<>();
        collectFromAnnotations(testClass.declaredAnnotation(TEST_BEAN_DOT), out);
        AnnotationInstance container = testClass.declaredAnnotation(TEST_BEANS_DOT);
        if (container != null) {
            AnnotationValue value = container.value();
            if (value != null) {
                for (AnnotationInstance nested : value.asNestedArray()) {
                    collectFromAnnotations(nested, out);
                }
            }
        }
        return out;
    }

    private static void collectFromAnnotations(
            AnnotationInstance testBean, Set<String> sink) {
        if (testBean == null) {
            return;
        }
        AnnotationValue bean = testBean.value("bean");
        if (bean != null) {
            Type type = bean.asClass();
            if (!"void".equals(type.name().toString())) {
                sink.add(type.name().toString());
            }
        }
    }

    private static boolean isFrameworkAllowed(String fqn) {
        for (String prefix : FRAMEWORK_ALLOWLIST_PREFIXES) {
            if (fqn.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

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
