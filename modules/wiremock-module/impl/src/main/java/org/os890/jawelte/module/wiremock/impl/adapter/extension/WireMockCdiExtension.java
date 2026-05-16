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

import jakarta.annotation.Priority;
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
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

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
 *       qualifier, three synthetic {@code @Dependent} beans are
 *       registered: one for {@link WireMockServer}, one for
 *       {@link WireMock}, and one for
 *       {@link WireMockRuntimeInfo} (the upstream metadata
 *       view). Each carries a Proxy-built literal of the user
 *       qualifier; the {@code produceWith} function reads the
 *       cached {@code EndpointResources} bundle off
 *       {@link WireMockServerRegistry#getFor(Class)} keyed by
 *       the endpoint identity and returns the bundled
 *       {@code server()} / {@code client()} / {@code runtimeInfo()}
 *       instance — no fresh construction per injection point.
 *
 *       <p>Whether the synthetic beans additionally carry
 *       {@code @Default} depends on
 *       {@link #resolveDefaultWinner(Iterable)}:
 *       <ul>
 *         <li><b>Single-qualifier mode</b>: the lone bean always
 *             gets {@code @Default} so unqualified injection
 *             resolves to it.</li>
 *         <li><b>Multi-qualifier mode with a
 *             {@link Priority @Priority} winner</b>: the
 *             qualifier with the strict-minimum
 *             {@code @Priority} value (and only that one) gets
 *             {@code @Default}; the rest carry only their user
 *             qualifier. Unqualified injection resolves to the
 *             priority winner.</li>
 *         <li><b>Multi-qualifier mode without a clear winner</b>
 *             (no {@code @Priority} anywhere, or two qualifiers
 *             tied at the lowest value): every synthetic bean
 *             gets {@code @Default}, surfacing the standard
 *             CDI {@code AmbiguousResolutionException} on
 *             unqualified injection at deployment time.</li>
 *       </ul>
 *       Qualified injection (the injection point names a
 *       specific {@code @WireMockEndpoint} qualifier) always
 *       follows standard CDI resolution and is unaffected by
 *       the priority logic.</li>
 * </ol>
 *
 * <p><b>Producer veto.</b> {@link WireMockProducer} ships
 * {@code @Default @Produces} methods for {@code WireMockServer},
 * {@code WireMock}, and {@code WireMockRuntimeInfo} — the
 * default-only path. When at least one {@code @WireMockEndpoint}
 * qualifier is discovered the producer is vetoed via
 * {@code ProcessAnnotatedType.veto()} so the synthetic beans alone
 * drive resolution. In default-only mode (no qualifier discovered)
 * the producer is left intact and serves all three injection
 * types.
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

    private static final Set<Class<?>> INJECTABLE_WIREMOCK_TYPES = Set.of(
            WireMockServer.class,
            WireMock.class,
            WireMockRuntimeInfo.class);

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
                if (!INJECTABLE_WIREMOCK_TYPES.contains(field.getType())) {
                    continue;
                }
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
        Class<? extends Annotation> defaultWinner = resolveDefaultWinner(discoveredEndpoints.keySet());
        boolean hasWinner = defaultWinner != null;

        for (Map.Entry<Class<? extends Annotation>, Class<? extends Annotation>> entry
                : discoveredEndpoints.entrySet()) {
            Class<? extends Annotation> userQualifierType = entry.getKey();
            Class<? extends Annotation> endpointKey = entry.getValue();
            Annotation userQualifierLiteral = literalFor(userQualifierType);
            Annotation[] qualifiers = qualifierArrayFor(
                    userQualifierLiteral, userQualifierType, defaultWinner, hasWinner);

            event.addBean()
                    .types(WireMockServer.class)
                    .qualifiers(qualifiers)
                    .scope(Dependent.class)
                    .produceWith(ctx -> resolveRegistry().getFor(endpointKey).server());

            event.addBean()
                    .types(WireMock.class)
                    .qualifiers(qualifiers)
                    .scope(Dependent.class)
                    .produceWith(ctx -> resolveRegistry().getFor(endpointKey).client());

            event.addBean()
                    .types(WireMockRuntimeInfo.class)
                    .qualifiers(qualifiers)
                    .scope(Dependent.class)
                    .produceWith(ctx -> resolveRegistry().getFor(endpointKey).runtimeInfo());
        }
    }

    /**
     * Decide which discovered qualifier (if any) becomes the
     * implicit {@code @Default} when an unqualified injection
     * point asks for one of the bridged WireMock types in
     * multi-endpoint mode. The rule: a qualifier carries
     * {@link Priority @Priority}; among the qualifiers with the
     * lowest priority value, exactly one must hold that minimum
     * for a winner to be declared. No {@code @Priority} on any
     * qualifier, or two-or-more qualifiers tied at the lowest
     * value, returns {@code null} — every synthetic bean then
     * keeps {@code @Default} (the pre-priority behaviour) and
     * unqualified injection in multi-endpoint mode raises the
     * usual CDI {@code AmbiguousResolutionException}.
     *
     * <p>Single-endpoint mode short-circuits this — when only one
     * qualifier was discovered, that one always wins by virtue of
     * being the only candidate, regardless of whether it carries
     * {@code @Priority}.
     *
     * @return the qualifier annotation type that should carry
     *         {@code @Default}, or {@code null} when no winner
     *         exists and the legacy "every synthetic bean has
     *         {@code @Default}" path should apply
     */
    private static Class<? extends Annotation> resolveDefaultWinner(
            Iterable<Class<? extends Annotation>> qualifiers) {
        Class<? extends Annotation> lowestPriorityQualifier = null;
        int lowestPriorityValue = Integer.MAX_VALUE;
        int countAtLowest = 0;
        int totalQualifiers = 0;
        for (Class<? extends Annotation> qualifier : qualifiers) {
            totalQualifiers++;
            Priority priorityAnnotation = qualifier.getAnnotation(Priority.class);
            if (priorityAnnotation == null) {
                continue;
            }
            int value = priorityAnnotation.value();
            if (value < lowestPriorityValue) {
                lowestPriorityValue = value;
                lowestPriorityQualifier = qualifier;
                countAtLowest = 1;
            } else if (value == lowestPriorityValue) {
                countAtLowest++;
            }
        }
        if (totalQualifiers <= 1) {
            // single-endpoint mode: caller treats hasWinner==false
            // as "give the lone qualifier @Default", which lands
            // at the same observable behaviour anyway.
            return null;
        }
        if (lowestPriorityQualifier == null) {
            return null;
        }
        if (countAtLowest != 1) {
            return null;
        }
        return lowestPriorityQualifier;
    }

    private static Annotation[] qualifierArrayFor(
            Annotation userQualifierLiteral,
            Class<? extends Annotation> userQualifierType,
            Class<? extends Annotation> defaultWinner,
            boolean hasWinner) {
        boolean addDefault = !hasWinner || userQualifierType == defaultWinner;
        if (addDefault) {
            return new Annotation[]{Default.Literal.INSTANCE, userQualifierLiteral};
        }
        return new Annotation[]{userQualifierLiteral};
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
