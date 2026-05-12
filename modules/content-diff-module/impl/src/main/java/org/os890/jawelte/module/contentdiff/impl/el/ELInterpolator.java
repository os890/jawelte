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
package org.os890.jawelte.module.contentdiff.impl.el;

import java.util.Map;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;

/**
 * Wraps Jakarta EL with the {@code ${expr}} interpolation flow the
 * two built-in {@link org.os890.jawelte.module.contentdiff.api.port.DiffEngine}
 * implementations need.
 *
 * <p>Interpolation is full Jakarta EL — ternaries, method calls,
 * property access. No sandboxing: methods on objects passed via
 * {@code values} are callable from the expected template. Missing
 * variables surface as the EL implementation's
 * {@code jakarta.el.PropertyNotFoundException} thrown from
 * {@link #interpolate(String, Map)}.
 *
 * <p>Each call creates a fresh {@link ExpressionFactory} and
 * {@link StandardELContext}; the helper holds no shared mutable state.
 */
public abstract class ELInterpolator {

    private static final char DOLLAR = '$';

    private static final char OPENING_BRACE = '{';

    private static final char CLOSING_BRACE = '}';

    private ELInterpolator() {
    }

    /**
     * Replace every {@code ${expression}} occurrence in
     * {@code template} with the EL-evaluated value computed against
     * {@code values}. Both literals around the expressions and the
     * empty case (no expressions present) are returned verbatim.
     *
     * @param template the expected document template
     * @param values   bindings the EL context can resolve
     * @return the interpolated string
     * @throws ELException        on EL evaluation failure (missing
     *                            variable, method-not-found, parse
     *                            error)
     */
    public static String interpolate(String template, Map<String, Object> values) {
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
