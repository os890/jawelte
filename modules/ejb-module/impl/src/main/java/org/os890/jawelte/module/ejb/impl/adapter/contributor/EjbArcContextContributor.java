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
package org.os890.jawelte.module.ejb.impl.adapter.contributor;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor;

import io.quarkus.arc.processor.BeanProcessor;

/**
 * ejb-module's {@link ArcContextContributor}: replaces the
 * {@code ProcessAnnotatedType} half of the legacy
 * {@code EjbAnnotationExtension} portable CDI Extension. ArC's
 * standalone bootstrap dispatches only {@code BeforeBeanDiscovery} /
 * {@code AfterBeanDiscovery} / {@code AfterDeploymentValidation} to
 * portable extensions and never invokes {@code ProcessAnnotatedType},
 * so the legacy extension's annotation rewriting never fires under
 * the ArC-based test container. This contributor performs the
 * equivalent rewrite at ArC's {@code AnnotationTransformation}
 * surface, called from cdi-module/impl just before
 * {@code BeanProcessor.process()}.
 *
 * <p>Two class-level mappings are applied:
 * <ul>
 *   <li>{@code @jakarta.ejb.Singleton} — add
 *       {@code @ApplicationScoped} when no other CDI scope is
 *       directly declared; in addition, add
 *       {@code @jakarta.transaction.Transactional} when the class
 *       does not already declare it.</li>
 *   <li>{@code @jakarta.ejb.Stateless} — add {@code @Dependent}
 *       when no other CDI scope is directly declared; in addition,
 *       add {@code @jakarta.transaction.Transactional} when the
 *       class does not already declare it.</li>
 * </ul>
 *
 * <p>Adding a CDI scope simultaneously makes the class a managed
 * bean: ArC indexes the class file from {@code target/test-classes}
 * but only registers a bean when the post-transformation annotation
 * set contains a bean-defining annotation. {@code @jakarta.ejb.Singleton}
 * is NOT a CDI bean-defining annotation; the transformation here
 * promotes it into one.
 *
 * <p>scope-module integration: when scope-module/api is on the
 * runtime classpath, {@code @jakarta.ejb.Singleton}-mapped beans
 * get {@code @TestClassScoped} instead of
 * {@code @ApplicationScoped} — mirroring
 * {@link org.os890.jawelte.module.ejb.impl.DefaultEjbAnnotationMapper}'s
 * MP-Config-driven scope selection. Resolved reflectively, no
 * compile-time dependency on scope-module.
 *
 * <p>The {@code @Stateless} → {@code @Dependent} mapping is
 * unconditional: a per-injection-point fresh instance does not
 * benefit from a long-lived test scope, matching the default
 * mapper's choice.
 *
 * <p>Discovered via
 * {@code META-INF/services/org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor}.
 */
public class EjbArcContextContributor implements ArcContextContributor {

    private static final DotName EJB_SINGLETON = DotName.createSimple("jakarta.ejb.Singleton");

    private static final DotName EJB_STATELESS = DotName.createSimple("jakarta.ejb.Stateless");

    private static final DotName APPLICATION_SCOPED =
            DotName.createSimple("jakarta.enterprise.context.ApplicationScoped");

    private static final DotName DEPENDENT =
            DotName.createSimple("jakarta.enterprise.context.Dependent");

    private static final DotName JAKARTA_TRANSACTIONAL =
            DotName.createSimple("jakarta.transaction.Transactional");

    /**
     * scope-module's {@code @TestClassScoped} FQN. Loaded
     * reflectively at class-load time; falls back to
     * {@code @ApplicationScoped} when scope-module is not on the
     * runtime classpath.
     */
    private static final String TEST_CLASS_SCOPED_FQN =
            "org.os890.jawelte.module.scope.api.TestClassScoped";

    private static final DotName SINGLETON_TARGET_SCOPE = resolveSingletonTargetScope();

    /** No-arg constructor required by {@code ServiceLoader}. */
    public EjbArcContextContributor() {
    }

    @Override
    public void contribute(TestContext testContext, BeanProcessor.Builder builder) {
        builder.addAnnotationTransformation(singletonTransformer());
        builder.addAnnotationTransformation(statelessTransformer());
    }

    private static AnnotationTransformation singletonTransformer() {
        return AnnotationTransformation.forClasses()
                .whenClass(c -> c.hasDeclaredAnnotation(EJB_SINGLETON))
                .transform(ctx -> {
                    ClassInfo classInfo = ctx.declaration().asClass();
                    if (!hasUserDeclaredScope(classInfo)) {
                        ctx.add(AnnotationInstance.builder(SINGLETON_TARGET_SCOPE).build());
                    }
                    if (!classInfo.hasDeclaredAnnotation(JAKARTA_TRANSACTIONAL)) {
                        ctx.add(AnnotationInstance.builder(JAKARTA_TRANSACTIONAL).build());
                    }
                });
    }

    private static AnnotationTransformation statelessTransformer() {
        return AnnotationTransformation.forClasses()
                .whenClass(c -> c.hasDeclaredAnnotation(EJB_STATELESS))
                .transform(ctx -> {
                    ClassInfo classInfo = ctx.declaration().asClass();
                    if (!hasUserDeclaredScope(classInfo)) {
                        ctx.add(AnnotationInstance.builder(DEPENDENT).build());
                    }
                    if (!classInfo.hasDeclaredAnnotation(JAKARTA_TRANSACTIONAL)) {
                        ctx.add(AnnotationInstance.builder(JAKARTA_TRANSACTIONAL).build());
                    }
                });
    }

    private static boolean hasUserDeclaredScope(ClassInfo classInfo) {
        for (AnnotationInstance annotation : classInfo.declaredAnnotations()) {
            String name = annotation.name().toString();
            if (isCdiScopeAnnotation(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCdiScopeAnnotation(String fqn) {
        return fqn.equals("jakarta.enterprise.context.ApplicationScoped")
                || fqn.equals("jakarta.enterprise.context.RequestScoped")
                || fqn.equals("jakarta.enterprise.context.SessionScoped")
                || fqn.equals("jakarta.enterprise.context.ConversationScoped")
                || fqn.equals("jakarta.enterprise.context.Dependent")
                || fqn.equals("jakarta.inject.Singleton")
                || fqn.equals(TEST_CLASS_SCOPED_FQN)
                || fqn.equals("org.os890.jawelte.module.scope.api.TestMethodScoped");
    }

    private static DotName resolveSingletonTargetScope() {
        try {
            Class.forName(TEST_CLASS_SCOPED_FQN, false,
                    EjbArcContextContributor.class.getClassLoader());
            return DotName.createSimple(TEST_CLASS_SCOPED_FQN);
        } catch (ClassNotFoundException | LinkageError absent) {
            return APPLICATION_SCOPED;
        }
    }
}
