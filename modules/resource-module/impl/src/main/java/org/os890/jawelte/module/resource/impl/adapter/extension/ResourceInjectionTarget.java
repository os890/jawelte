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
package org.os890.jawelte.module.resource.impl.adapter.extension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InjectionTarget;

import org.os890.jawelte.module.resource.api.port.ResourceLookup;
import org.os890.jawelte.module.resource.impl.ResourceFields;

/**
 * Wraps the {@link InjectionTarget} the CDI runtime built for a type
 * that declares {@code @Resource} fields, and fills those fields after
 * the runtime has done its own injection.
 *
 * <p>Everything else delegates untouched. Only types that actually
 * declare a named {@code @Resource} field are wrapped at all — see
 * {@link ResourceInjectionCdiExtension} — so a deployment without one
 * runs on the runtime's own injection targets exactly as before.
 *
 * <p>Ordering matters and is deliberate: {@code @Resource} fields are
 * set <em>after</em> {@code delegate.inject(...)}, so a bean whose
 * {@code @PostConstruct} uses a resource finds it in place, and a
 * {@code @Resource} field never overwrites a CDI-injected one on the
 * pathological type that annotates a field with both.
 *
 * @param <X> the bean type
 */
class ResourceInjectionTarget<X> implements InjectionTarget<X> {

    private final InjectionTarget<X> delegate;
    private final List<ResourceFields.Target> targets;
    private final ResourceLookup resourceLookup;
    private final Class<?> declaringClass;

    ResourceInjectionTarget(
            InjectionTarget<X> delegate,
            List<ResourceFields.Target> targets,
            ResourceLookup resourceLookup,
            Class<?> declaringClass) {
        this.delegate = delegate;
        this.targets = targets;
        this.resourceLookup = resourceLookup;
        this.declaringClass = declaringClass;
    }

    @Override
    public void inject(X instance, CreationalContext<X> creationalContext) {
        delegate.inject(instance, creationalContext);
        for (ResourceFields.Target target : targets) {
            injectOne(instance, target);
        }
    }

    private void injectOne(X instance, ResourceFields.Target target) {
        Field field = target.field();
        String name = target.name();
        Object resolved = resourceLookup.lookup(name, field.getType());
        if (resolved == null) {
            throw new IllegalStateException(
                    "@Resource(\"" + name + "\") on " + declaringClass.getName() + "." + field.getName()
                            + " could not be resolved: nothing is bound under that name. A"
                            + " @DataSourceDefinition binds under its own name, so check that the two"
                            + " spellings match — and that JNDI binding is on"
                            + " (org.os890.jawelte.module.datasource.jndi.enabled).");
        }
        if (!field.getType().isInstance(resolved)) {
            throw new IllegalStateException(
                    "@Resource(\"" + name + "\") on " + declaringClass.getName() + "." + field.getName()
                            + " resolved to a " + resolved.getClass().getName()
                            + ", which is not a " + field.getType().getName());
        }
        try {
            field.setAccessible(true);
            field.set(instance, resolved);
        } catch (ReflectiveOperationException | RuntimeException injectionFailure) {
            throw new IllegalStateException(
                    "@Resource(\"" + name + "\") on " + declaringClass.getName() + "." + field.getName()
                            + " resolved but could not be assigned",
                    injectionFailure);
        }
    }

    @Override
    public X produce(CreationalContext<X> creationalContext) {
        return delegate.produce(creationalContext);
    }

    @Override
    public void postConstruct(X instance) {
        delegate.postConstruct(instance);
    }

    @Override
    public void preDestroy(X instance) {
        delegate.preDestroy(instance);
    }

    @Override
    public void dispose(X instance) {
        delegate.dispose(instance);
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        // Unchanged on purpose: @Resource fields are not CDI injection
        // points, so they must not appear here. Declaring them would
        // put them through CDI's typesafe resolution, which is the
        // opposite of what the annotation asks for.
        return delegate.getInjectionPoints();
    }
}
