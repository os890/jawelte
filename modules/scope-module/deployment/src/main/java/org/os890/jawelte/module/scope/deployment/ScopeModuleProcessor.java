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
package org.os890.jawelte.module.scope.deployment;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.DotName;
import org.os890.jawelte.module.scope.api.TestClassScoped;
import org.os890.jawelte.module.scope.api.TestMethodScoped;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopeContextCreator;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopeContextCreator;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.ContextRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.ContextRegistrationPhaseBuildItem.ContextConfiguratorBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;

/**
 * Quarkus deployment processor for scope-module. Two build steps:
 * register jawelte's two normal scopes with ArC, and tell ArC to
 * treat every class annotated with one of those scopes as a bean.
 *
 * <p>The runtime sides — the {@code TestClassScopedContext} /
 * {@code TestMethodScopedContext} instances, the stores, the
 * {@code ScopeLifecycleAdapter} — all live in
 * {@code scope-module/impl} (the runtime artifact). This class lives
 * in {@code scope-module/deployment} because Quarkus auto-discovers
 * extensions via the deployment artifact's classpath and only allows
 * {@code @BuildStep} methods on classes that ship from a deployment
 * artifact.
 *
 * <p>Under {@code @QuarkusTest} this build step runs once per test
 * class (Quarkus rebuilds per test class in test mode); the
 * registered {@code ContextCreator}s are instantiated by ArC when
 * the container boots and read the per-test-class store handles
 * from {@code TestScopeCurrentStores}.
 */
public class ScopeModuleProcessor {

    private static final DotName TEST_CLASS_SCOPED_DOT =
            DotName.createSimple(TestClassScoped.class.getName());

    private static final DotName TEST_METHOD_SCOPED_DOT =
            DotName.createSimple(TestMethodScoped.class.getName());

    private static final DotName CONFIG_BEAN_DOT =
            DotName.createSimple("org.os890.jawelte.core.api.ConfigBean");

    private static final DotName APPLICATION_SCOPED_DOT =
            DotName.createSimple("jakarta.enterprise.context.ApplicationScoped");

    /** No-arg constructor required by Quarkus's reflective discovery. */
    public ScopeModuleProcessor() {
    }

    /**
     * Register jawelte's two test-lifecycle scopes with ArC. Quarkus
     * provides the {@code ContextRegistrationPhaseBuildItem} as the
     * single-instance entry point into ArC's context-registration
     * pipeline; the build step produces a
     * {@link ContextConfiguratorBuildItem} carrying the two
     * configurators.
     *
     * @param phase Quarkus's context-registration phase build item
     * @return one {@code ContextConfiguratorBuildItem} carrying the
     *         {@code @TestClassScoped} + {@code @TestMethodScoped}
     *         configurators
     */
    @BuildStep
    public ContextConfiguratorBuildItem registerScopes(ContextRegistrationPhaseBuildItem phase) {
        return new ContextConfiguratorBuildItem(
                phase.getContext().configure(TestClassScoped.class)
                        .normal()
                        .creator(TestClassScopeContextCreator.class),
                phase.getContext().configure(TestMethodScoped.class)
                        .normal()
                        .creator(TestMethodScopeContextCreator.class));
    }

    /**
     * Add every class annotated with {@code @TestClassScoped} or
     * {@code @TestMethodScoped} to ArC's bean archive. Quarkus's
     * default bean discovery doesn't include the test class's
     * static-nested types automatically; this build step closes
     * that gap so the auto-mock {@code BuildCompatibleExtension}
     * doesn't mistakenly mock a real user bean.
     *
     * @param combinedIndex the application's combined Jandex index
     * @return an {@link AdditionalBeanBuildItem} carrying every
     *         scope-annotated class found in the index
     */
    /**
     * Remap {@code @ConfigBean}-stereotyped classes from the
     * stereotype-contributed {@code @ApplicationScoped} to
     * {@code @TestClassScoped}. Mirrors scope-module's standalone
     * CDI Extension that wires the same remap during
     * {@code ProcessAnnotatedType}. scope-28 / scope-29 lock this
     * behavior in.
     *
     * <p>Honors the addendum precedence: if the user has explicitly
     * declared a different scope on the {@code @ConfigBean} class
     * (e.g. {@code @ApplicationScoped @ConfigBean class X}, where
     * the {@code @ApplicationScoped} is directly on the class
     * rather than contributed by the stereotype), the user-declared
     * scope wins. We detect "directly declared" by checking the
     * class's own annotations for a CDI scope; if exactly one CDI
     * scope is found and it's the one contributed by the stereotype
     * (i.e. {@code @ApplicationScoped} with no user-declared scope
     * elsewhere), we replace it with {@code @TestClassScoped}.
     */
    @BuildStep
    public AnnotationsTransformerBuildItem remapConfigBeanScope() {
        return new AnnotationsTransformerBuildItem(
                AnnotationTransformation.forClasses()
                        .whenClass(c -> c.hasAnnotation(CONFIG_BEAN_DOT)
                                && !hasUserDeclaredScope(c))
                        .transform(ctx -> {
                            ctx.remove(ann -> APPLICATION_SCOPED_DOT.equals(ann.name()));
                            ctx.add(org.jboss.jandex.AnnotationInstance.builder(
                                    TEST_CLASS_SCOPED_DOT).build());
                        }));
    }

    private static boolean hasUserDeclaredScope(org.jboss.jandex.ClassInfo classInfo) {
        for (org.jboss.jandex.AnnotationInstance annotation : classInfo.declaredAnnotations()) {
            String name = annotation.name().toString();
            if ("jakarta.enterprise.context.RequestScoped".equals(name)
                    || "jakarta.enterprise.context.SessionScoped".equals(name)
                    || "jakarta.enterprise.context.ConversationScoped".equals(name)
                    || "jakarta.enterprise.context.Dependent".equals(name)
                    || "jakarta.inject.Singleton".equals(name)
                    || TEST_CLASS_SCOPED_DOT.equals(annotation.name())
                    || TEST_METHOD_SCOPED_DOT.equals(annotation.name())) {
                return true;
            }
        }
        return false;
    }

    @BuildStep
    public AdditionalBeanBuildItem registerScopedBeansAsBeans(CombinedIndexBuildItem combinedIndex) {
        Set<String> beanClassNames = new LinkedHashSet<>();
        for (DotName scope : List.of(TEST_CLASS_SCOPED_DOT, TEST_METHOD_SCOPED_DOT)) {
            for (AnnotationInstance annotation : combinedIndex.getIndex().getAnnotations(scope)) {
                if (annotation.target().kind() == AnnotationTarget.Kind.CLASS) {
                    beanClassNames.add(annotation.target().asClass().name().toString());
                }
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
