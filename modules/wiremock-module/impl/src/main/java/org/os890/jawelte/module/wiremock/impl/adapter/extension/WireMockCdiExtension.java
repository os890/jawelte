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
package org.os890.jawelte.module.wiremock.impl.adapter.extension;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.inject.Qualifier;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;
import org.os890.jawelte.module.wiremock.api.WireMockEndpoint;
import org.os890.jawelte.module.wiremock.impl.WireMockProducer;
import org.os890.jawelte.module.wiremock.impl.WireMockServerRegistry;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * CDI Extension shipped by wiremock-module/impl. Owns two
 * responsibilities:
 *
 * <ol>
 *   <li><b>Endpoint discovery</b> — during
 *       {@code BeforeBeanDiscovery} the extension reads the active
 *       {@link TestContext} (resolved via the static
 *       {@link TestContext#get()} accessor), walks the test class
 *       hierarchy's declared fields, and collects every
 *       {@code @Qualifier} annotation that ultimately leads (directly
 *       or transitively via meta-annotation) to a
 *       {@link WireMockEndpoint @WireMockEndpoint}-stamped
 *       annotation. The resulting
 *       {@code (userQualifierType -> endpointKey)} map drives both
 *       the synthetic-bean registration here and the
 *       {@code WireMockServer} startup in
 *       {@code WireMockLifecycleAdapter.beforeAll} (the adapter
 *       reads the map back via
 *       {@code BeanManager.getExtension(...)}).</li>
 *   <li><b>Synthetic-bean registration</b> — during
 *       {@code AfterBeanDiscovery}, for every discovered user
 *       qualifier, two synthetic {@code @Dependent} beans are
 *       registered: one for {@link WireMockServer} and one for
 *       {@link WireMock}. Each carries
 *       {@code @Default} <b>plus</b> a Proxy-built literal of the
 *       user qualifier; the {@code produceWith} function looks the
 *       running server up in
 *       {@link WireMockServerRegistry#getFor(Class)} keyed by the
 *       endpoint identity. Single-qualifier mode resolves
 *       {@code @Inject WireMockServer} (no qualifier) to the lone
 *       synthetic bean; multi-qualifier mode produces
 *       {@code AmbiguousResolutionException} on unqualified
 *       injection because every synthetic bean carries
 *       {@code @Default}.</li>
 * </ol>
 *
 * <p><b>Producer veto.</b> {@link WireMockProducer} ships
 * {@code @Default @Produces} methods for {@code WireMockServer} and
 * {@code WireMock} — the default-only path. When at least one
 * {@code @WireMockEndpoint} qualifier is discovered the producer is
 * vetoed via {@code ProcessAnnotatedType.veto()} so the synthetic
 * beans alone drive resolution. In default-only mode (no qualifier
 * discovered) the producer is left intact and serves both
 * injection points.
 *
 * <p><b>Default-only mode.</b> When the discovered map is empty
 * (the typical {@code @EnableWireMock} case with no user qualifier
 * anywhere in the hierarchy), the extension registers <b>no</b>
 * synthetic beans. {@code WireMockProducer} alone satisfies
 * {@code @Inject WireMockServer} / {@code @Inject WireMock}.
 *
 * <p><b>No-test-context safety.</b> If
 * {@code TestContext.get()} throws (a non-jawelte CDI bootstrap, or
 * the test class isn't annotated {@code @EnableWireMock}), the
 * discovery is skipped and the map stays empty — the producer
 * survives unchallenged and no synthetic beans are registered.
 *
 * <p>Discovered via
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * shipped in this module.
 */
public class WireMockCdiExtension implements Extension {

    private final Map<Class<? extends Annotation>, Class<? extends Annotation>> discoveredEndpoints =
            new LinkedHashMap<>();

    private final ConcurrentMap<Class<? extends Annotation>, Annotation> literalCache =
            new ConcurrentHashMap<>();

    /** No-arg constructor required by the CDI runtime. */
    public WireMockCdiExtension() {
    }

    void onBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        TestContext context;
        try {
            context = TestContext.get();
        } catch (IllegalStateException notInBootstrap) {
            return;
        }
        Class<?> testClass = context.getTestClass();
        if (testClass.getAnnotation(EnableWireMock.class) == null) {
            return;
        }
        for (Class<?> current = testClass; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                for (Annotation annotation : field.getAnnotations()) {
                    collectEndpoint(annotation.annotationType(), discoveredEndpoints, new HashSet<>());
                }
            }
        }
    }

    void onProcessProducerType(@Observes ProcessAnnotatedType<WireMockProducer> event) {
        if (!discoveredEndpoints.isEmpty()) {
            event.veto();
        }
    }

    void onAfterBeanDiscovery(@Observes AfterBeanDiscovery event) {
        for (Map.Entry<Class<? extends Annotation>, Class<? extends Annotation>> entry
                : discoveredEndpoints.entrySet()) {
            Class<? extends Annotation> userQualifierType = entry.getKey();
            Class<? extends Annotation> endpointKey = entry.getValue();
            Annotation userQualifierLiteral = literalFor(userQualifierType);

            event.addBean()
                    .types(WireMockServer.class)
                    .qualifiers(Default.Literal.INSTANCE, userQualifierLiteral)
                    .scope(Dependent.class)
                    .produceWith(ctx -> resolveRegistry().getFor(endpointKey));

            event.addBean()
                    .types(WireMock.class)
                    .qualifiers(Default.Literal.INSTANCE, userQualifierLiteral)
                    .scope(Dependent.class)
                    .produceWith(ctx -> new WireMock(resolveRegistry().getFor(endpointKey).port()));
        }
    }

    /**
     * The discovered endpoint map — an unmodifiable view of the
     * {@code (userQualifierType -> endpointKey)} pairs collected
     * from the test class hierarchy in
     * {@code BeforeBeanDiscovery}. Read by
     * {@code WireMockLifecycleAdapter.beforeAll} (via
     * {@code BeanManager.getExtension(WireMockCdiExtension.class)})
     * to decide which {@code WireMockServer} instances to start.
     *
     * @return an unmodifiable {@code Map} view of the discovered
     *         endpoints; empty when no qualifier was discovered
     *         (default-only mode)
     */
    public Map<Class<? extends Annotation>, Class<? extends Annotation>> discoveredEndpoints() {
        return Collections.unmodifiableMap(discoveredEndpoints);
    }

    private static void collectEndpoint(
            Class<? extends Annotation> annotationType,
            Map<Class<? extends Annotation>, Class<? extends Annotation>> sink,
            Set<Class<? extends Annotation>> visited) {
        if (!visited.add(annotationType)) {
            return;
        }
        Class<? extends Annotation> endpointKey = findEndpointAncestor(annotationType, new HashSet<>());
        if (endpointKey == null) {
            return;
        }
        if (annotationType.isAnnotationPresent(Qualifier.class)) {
            sink.putIfAbsent(annotationType, endpointKey);
        }
    }

    private static Class<? extends Annotation> findEndpointAncestor(
            Class<? extends Annotation> annotationType, Set<Class<? extends Annotation>> visited) {
        if (!visited.add(annotationType)) {
            return null;
        }
        if (annotationType.isAnnotationPresent(WireMockEndpoint.class)) {
            return annotationType;
        }
        for (Annotation meta : annotationType.getAnnotations()) {
            String packageName = meta.annotationType().getPackageName();
            if (packageName.startsWith("java.lang") || packageName.startsWith("jakarta.")) {
                continue;
            }
            Class<? extends Annotation> result = findEndpointAncestor(meta.annotationType(), visited);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private Annotation literalFor(Class<? extends Annotation> annotationType) {
        return literalCache.computeIfAbsent(annotationType, WireMockCdiExtension::buildLiteral);
    }

    private static Annotation buildLiteral(Class<? extends Annotation> annotationType) {
        return (Annotation) Proxy.newProxyInstance(
                annotationType.getClassLoader(),
                new Class<?>[]{annotationType},
                (proxy, method, args) -> switch (method.getName()) {
                    case "annotationType" -> annotationType;
                    case "toString" -> "@" + annotationType.getName() + "()";
                    case "hashCode" -> 0;
                    case "equals" -> args != null
                            && args.length == 1
                            && args[0] instanceof Annotation other
                            && other.annotationType().equals(annotationType);
                    default -> method.getDefaultValue();
                });
    }

    private static WireMockServerRegistry resolveRegistry() {
        return CDI.current().select(WireMockServerRegistry.class).get();
    }
}
