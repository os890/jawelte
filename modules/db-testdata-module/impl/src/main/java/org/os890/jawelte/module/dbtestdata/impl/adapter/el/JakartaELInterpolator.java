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
package org.os890.jawelte.module.dbtestdata.impl.adapter.el;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Priority;
import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.el.FunctionMapper;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;

import org.os890.jawelte.module.dbtestdata.api.ELFunctionDescriptor;
import org.os890.jawelte.module.dbtestdata.api.InterpolationContext;
import org.os890.jawelte.module.dbtestdata.api.port.ELInterpolator;

/**
 * Default {@link ELInterpolator} — evaluates {@code ${expr}}
 * occurrences in the dataset text through Jakarta EL.
 *
 * <p>The concrete EL provider is selected by Jakarta EL's own
 * {@code ExpressionFactory.newInstance()} mechanism: whichever
 * {@code jakarta.el.ExpressionFactory} sits on the classpath wins.
 * Tomcat's {@code tomcat-embed-el} is the project's default
 * test-scope provider and GlassFish Expressly is also supported.
 *
 * <p>Bindings:
 *
 * <ul>
 *   <li>{@link InterpolationContext#values()} → {@link VariableMapper}
 *       entries — flat name -&gt; object.</li>
 *   <li>{@link InterpolationContext#beans()} → also wired through
 *       the variable mapper; EL resolves property access /
 *       method calls on the bean object.</li>
 *   <li>{@link InterpolationContext#functions()} → exposed via a
 *       lazy {@link FunctionMapper}: the static method on
 *       {@link ELFunctionDescriptor#declaringClass()} is looked up
 *       on first reference; missing or non-{@code public static}
 *       methods raise {@link RuntimeException} at that point —
 *       evaluation time, never registration time.</li>
 * </ul>
 *
 * <p>Ships at {@code @Priority(Integer.MAX_VALUE)} and is the only
 * impl registered in
 * {@code META-INF/services/org.os890.jawelte.module.dbtestdata.api.port.ELInterpolator}
 * by default. Consumers register a competing impl at a lower
 * priority to override.
 *
 * <p>Stateless and thread-safe — a fresh {@link ExpressionFactory}
 * and {@link StandardELContext} are constructed per call.
 */
@Priority(Integer.MAX_VALUE)
public class JakartaELInterpolator implements ELInterpolator {

    private static final char DOLLAR = '$';

    private static final char HASH = '#';

    private static final char OPENING_BRACE = '{';

    private static final char CLOSING_BRACE = '}';

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JakartaELInterpolator() {
    }

    @Override
    public String interpolate(String template, InterpolationContext context) {
        if (template.indexOf(DOLLAR) < 0) {
            return template;
        }
        return substituteTemplate(template, context, false);
    }

    @Override
    public String interpolateAll(String template, InterpolationContext context) {
        if (template.indexOf(DOLLAR) < 0 && template.indexOf(HASH) < 0) {
            return template;
        }
        return substituteTemplate(template, context, true);
    }

    @Override
    public boolean evaluatePredicate(
            String expression, InterpolationContext context, Object actualValue) {
        ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
        StandardELContext standardELContext = new StandardELContext(expressionFactory);
        bindContext(standardELContext, expressionFactory, context);
        VariableMapper variableMapper = standardELContext.getVariableMapper();
        variableMapper.setVariable(
                "value", expressionFactory.createValueExpression(actualValue, Object.class));
        Double numericBinding = tryParseDouble(actualValue);
        if (numericBinding != null) {
            variableMapper.setVariable(
                    "num", expressionFactory.createValueExpression(numericBinding, Double.class));
        }
        ELContext elContext = context.functions().isEmpty()
                ? standardELContext
                : new DelegatingELContext(standardELContext, context.functions());
        ValueExpression valueExpression =
                expressionFactory.createValueExpression(elContext, expression, Object.class);
        Object result = valueExpression.getValue(elContext);
        if (result == null) {
            throw new RuntimeException(
                    "Predicate " + expression + " returned null; expected Boolean");
        }
        if (!(result instanceof Boolean)) {
            throw new RuntimeException(
                    "Predicate " + expression + " returned " + result.getClass().getName()
                            + "; expected Boolean");
        }
        return (Boolean) result;
    }

    private static Double tryParseDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String substituteTemplate(
            String template, InterpolationContext context, boolean includeHashSyntax) {
        ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
        StandardELContext standardELContext = new StandardELContext(expressionFactory);
        bindContext(standardELContext, expressionFactory, context);
        ELContext elContext = context.functions().isEmpty()
                ? standardELContext
                : new DelegatingELContext(standardELContext, context.functions());
        return substituteAll(template, expressionFactory, elContext, includeHashSyntax);
    }

    private static void bindContext(
            StandardELContext standardELContext,
            ExpressionFactory expressionFactory,
            InterpolationContext context) {
        VariableMapper variableMapper = standardELContext.getVariableMapper();
        for (Map.Entry<String, Object> entry : context.values().entrySet()) {
            variableMapper.setVariable(
                    entry.getKey(),
                    expressionFactory.createValueExpression(entry.getValue(), Object.class));
        }
        for (Map.Entry<String, Object> entry : context.beans().entrySet()) {
            variableMapper.setVariable(
                    entry.getKey(),
                    expressionFactory.createValueExpression(entry.getValue(), Object.class));
        }
    }

    private static String substituteAll(
            String template,
            ExpressionFactory expressionFactory,
            ELContext elContext,
            boolean includeHashSyntax) {
        StringBuilder output = new StringBuilder(template.length());
        int index = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            boolean dollarOpen = current == DOLLAR
                    && index + 1 < template.length()
                    && template.charAt(index + 1) == OPENING_BRACE;
            boolean hashOpen = includeHashSyntax
                    && current == HASH
                    && index + 1 < template.length()
                    && template.charAt(index + 1) == OPENING_BRACE;
            if (dollarOpen || hashOpen) {
                int closing = template.indexOf(CLOSING_BRACE, index + 2);
                if (closing == -1) {
                    output.append(template, index, template.length());
                    break;
                }
                String expression = template.substring(index, closing + 1);
                ValueExpression valueExpression = expressionFactory.createValueExpression(
                        elContext, expression, Object.class);
                Object resolved = valueExpression.getValue(elContext);
                output.append(resolved == null ? "" : resolved.toString());
                index = closing + 1;
            } else {
                output.append(current);
                index++;
            }
        }
        return output.toString();
    }

    private static Method resolveStaticMethod(ELFunctionDescriptor descriptor) {
        Method match = null;
        for (Method candidate : descriptor.declaringClass().getDeclaredMethods()) {
            if (!candidate.getName().equals(descriptor.methodName())) {
                continue;
            }
            if (!Modifier.isStatic(candidate.getModifiers())) {
                throw new RuntimeException(
                        "EL function " + descriptor.prefix() + ":" + descriptor.name()
                                + " — method '" + descriptor.methodName() + "' on "
                                + descriptor.declaringClass().getName() + " must be public static");
            }
            if (!Modifier.isPublic(candidate.getModifiers())) {
                throw new RuntimeException(
                        "EL function " + descriptor.prefix() + ":" + descriptor.name()
                                + " — method '" + descriptor.methodName() + "' on "
                                + descriptor.declaringClass().getName() + " must be public static");
            }
            match = candidate;
            break;
        }
        if (match == null) {
            throw new RuntimeException(
                    "EL function " + descriptor.prefix() + ":" + descriptor.name()
                            + " — no public static method '" + descriptor.methodName()
                            + "' on " + descriptor.declaringClass().getName());
        }
        return match;
    }

    /**
     * {@link ELContext} delegating to {@link StandardELContext} but
     * routing {@link #getFunctionMapper()} through a lazy lookup
     * built from the {@link ELFunctionDescriptor} list.
     */
    private static class DelegatingELContext extends ELContext {

        private final StandardELContext delegate;

        private final FunctionMapper functionMapper;

        DelegatingELContext(StandardELContext delegate, List<ELFunctionDescriptor> functions) {
            this.delegate = delegate;
            this.functionMapper = new LazyFunctionMapper(functions);
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return functionMapper;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return delegate.getVariableMapper();
        }

        @Override
        public ELResolver getELResolver() {
            return delegate.getELResolver();
        }
    }

    /**
     * Resolves Jakarta EL functions on first reference; caches the
     * resolved {@link Method} per {@code prefix:name} pair so a
     * function called multiple times in the same template does not
     * trigger repeated reflective lookups.
     */
    private static class LazyFunctionMapper extends FunctionMapper {

        private final List<ELFunctionDescriptor> descriptors;

        private final Map<String, Method> resolved = new HashMap<>();

        LazyFunctionMapper(List<ELFunctionDescriptor> descriptors) {
            this.descriptors = descriptors;
        }

        @Override
        public Method resolveFunction(String prefix, String localName) {
            String key = prefix + ":" + localName;
            Method cached = resolved.get(key);
            if (cached != null) {
                return cached;
            }
            for (ELFunctionDescriptor descriptor : descriptors) {
                if (descriptor.prefix().equals(prefix) && descriptor.name().equals(localName)) {
                    Method method = resolveStaticMethod(descriptor);
                    resolved.put(key, method);
                    return method;
                }
            }
            throw new ELException("Function '" + key + "' is not defined");
        }
    }
}
