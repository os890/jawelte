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
package org.os890.jawelte.module.contentdiff.impl.adapter.el;

import java.util.Map;

import jakarta.annotation.Priority;
import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;

import org.os890.jawelte.module.contentdiff.api.port.ELInterpolator;

/**
 * Default {@link ELInterpolator} — evaluates {@code ${expr}}
 * occurrences in the template through Jakarta EL.
 *
 * <p>The concrete EL provider is selected by the Jakarta EL spec's
 * own {@code ExpressionFactory.newInstance()} mechanism: whichever
 * {@code jakarta.el.ExpressionFactory} implementation sits on the
 * classpath wins. The project pins Apache Tomcat's
 * {@code tomcat-embed-el} as the default test-scope provider and
 * also ships GlassFish Expressly as an alternative — both work with
 * this impl unchanged.
 *
 * <p>Full Jakarta EL semantics: ternaries, method invocations,
 * property access, no sandboxing. Missing variables surface as
 * {@code jakarta.el.PropertyNotFoundException} from
 * {@link #interpolate(String, Map)}.
 *
 * <p>Ships at {@code @Priority(Integer.MAX_VALUE)} and is the only
 * implementation registered in
 * {@code META-INF/services/org.os890.jawelte.module.contentdiff.api.port.ELInterpolator}
 * by default. Consumers swap in their own interpolator (e.g. a
 * no-op for fixtures that should NOT be interpolated) by
 * registering it at a lower priority value.
 *
 * <p>Stateless and thread-safe — a fresh {@link ExpressionFactory}
 * and {@link StandardELContext} are constructed per call, so the
 * single shared instance holds no mutable state.
 */
@Priority(Integer.MAX_VALUE)
public class JakartaELInterpolator implements ELInterpolator {

    private static final char DOLLAR = '$';

    private static final char OPENING_BRACE = '{';

    private static final char CLOSING_BRACE = '}';

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JakartaELInterpolator() {
    }

    /**
     * {@inheritDoc}
     *
     * @throws ELException on EL evaluation failure — missing
     *                     variable, method not found, parse error
     */
    @Override
    public String interpolate(String template, Map<String, Object> values) {
        if (template.indexOf(DOLLAR) < 0) {
            return template;
        }
        ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
        StandardELContext elContext = new StandardELContext(expressionFactory);
        VariableMapper variableMapper = elContext.getVariableMapper();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            ValueExpression bindingExpression = expressionFactory.createValueExpression(
                    entry.getValue(), Object.class);
            variableMapper.setVariable(entry.getKey(), bindingExpression);
        }
        return resolveAll(template, expressionFactory, elContext);
    }

    private static String resolveAll(String template, ExpressionFactory expressionFactory, ELContext elContext) {
        StringBuilder output = new StringBuilder(template.length());
        int index = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            if (current == DOLLAR
                    && index + 1 < template.length()
                    && template.charAt(index + 1) == OPENING_BRACE) {
                int closing = template.indexOf(CLOSING_BRACE, index + 2);
                if (closing == -1) {
                    output.append(template, index, template.length());
                    break;
                }
                String elExpression = template.substring(index, closing + 1);
                ValueExpression valueExpression = expressionFactory.createValueExpression(
                        elContext, elExpression, Object.class);
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
}
