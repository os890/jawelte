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
package org.os890.jawelte.module.ejb.deployment;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.DotName;

import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.deployment.annotations.BuildStep;

/**
 * Quarkus deployment processor for ejb-module. Rewrites EJB
 * session-bean annotations on user beans to their CDI equivalents at
 * Jandex-transformation time so ArC's
 * {@code WrongAnnotationUsageProcessor} doesn't reject the class
 * (it hard-fails on {@code @jakarta.ejb.Singleton} with the message
 * "use @jakarta.inject.Singleton instead"). Mirrors the standalone-
 * portable {@code EjbAnnotationExtension}'s
 * {@code ProcessAnnotatedType} rewrite under OWB/Weld.
 *
 * <p>Rewrite rules (matching the OWB/Weld baseline):
 * <ul>
 *   <li>{@code @jakarta.ejb.Singleton} →
 *       {@code @jakarta.enterprise.context.ApplicationScoped}
 *       + {@code @jakarta.transaction.Transactional}</li>
 *   <li>{@code @jakarta.ejb.Stateless} →
 *       {@code @jakarta.enterprise.context.Dependent}
 *       + {@code @jakarta.transaction.Transactional}</li>
 * </ul>
 *
 * <p>The {@code @TransactionAttribute} annotation is left untouched —
 * jpa-module's {@code @Transactional} interceptor ignores
 * {@code @TransactionAttribute} attribute values (every call is
 * treated as REQUIRED with the {@code JtaTransactionStrategy}
 * suspending the outer for nested {@code REQUIRES_NEW}, matching the
 * documented behaviour).
 */
public class EjbModuleProcessor {

    private static final DotName EJB_SINGLETON =
            DotName.createSimple("jakarta.ejb.Singleton");
    private static final DotName EJB_STATELESS =
            DotName.createSimple("jakarta.ejb.Stateless");
    private static final DotName APPLICATION_SCOPED =
            DotName.createSimple("jakarta.enterprise.context.ApplicationScoped");
    private static final DotName DEPENDENT =
            DotName.createSimple("jakarta.enterprise.context.Dependent");
    private static final DotName TRANSACTIONAL =
            DotName.createSimple("jakarta.transaction.Transactional");

    /** No-arg constructor required by Quarkus's reflective discovery. */
    public EjbModuleProcessor() {
    }

    /**
     * Rewrite EJB session-bean annotations on every class that carries
     * one. Uses Jandex's {@link AnnotationTransformation#forClasses}
     * so the rewrite is visible to every subsequent build step,
     * including {@code WrongAnnotationUsageProcessor}.
     *
     * @return an {@link AnnotationsTransformerBuildItem} carrying the
     *         class-level rewrite
     */
    @BuildStep
    public AnnotationsTransformerBuildItem rewriteEjbAnnotations() {
        return new AnnotationsTransformerBuildItem(AnnotationTransformation.forClasses()
                .whenAnyMatch(ann -> ann.name().equals(EJB_SINGLETON)
                        || ann.name().equals(EJB_STATELESS))
                .transform(ctx -> {
                    boolean wasSingleton = ctx.annotations().stream()
                            .anyMatch(ann -> ann.name().equals(EJB_SINGLETON));
                    boolean wasStateless = ctx.annotations().stream()
                            .anyMatch(ann -> ann.name().equals(EJB_STATELESS));
                    if (wasSingleton) {
                        ctx.remove(ann -> ann.name().equals(EJB_SINGLETON));
                        ctx.add(AnnotationInstance.builder(APPLICATION_SCOPED).build());
                    }
                    if (wasStateless) {
                        ctx.remove(ann -> ann.name().equals(EJB_STATELESS));
                        ctx.add(AnnotationInstance.builder(DEPENDENT).build());
                    }
                    if (wasSingleton || wasStateless) {
                        ctx.add(AnnotationInstance.builder(TRANSACTIONAL).build());
                    }
                }));
    }
}
