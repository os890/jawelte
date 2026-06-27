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
 * <p>The <code>${...}</code> scan is brace-aware: a closing brace
 * inside the expression's own nested braces (map / set / list
 * literals) or inside a single- or double-quoted EL string literal
 * does not end the expression, so <code>${name.concat('}')}</code>
 * and <code>${ {'a':1}['a'] }</code> are passed to the EL parser
 * intact. A bare <code>${</code> with no matching closing brace is
 * treated as an unbalanced template: the remainder is copied verbatim.
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

    private static final char SINGLE_QUOTE = '\'';

    private static final char DOUBLE_QUOTE = '"';

    private static final char BACKSLASH = '\\';

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
                int closing = findExpressionEnd(template, index + 1);
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

    /**
     * Find the index of the {@code '}'} that closes the EL expression
     * whose opening {@code '{'} is at {@code openBraceIndex}. Braces
     * nested inside the expression (map / set / list literals) and
     * braces appearing inside single- or double-quoted EL string
     * literals do not end the expression. Returns {@code -1} when the
     * expression is unbalanced (no matching {@code '}'}), so the caller
     * copies the remainder of the template verbatim.
     *
     * @param template       the full template being scanned
     * @param openBraceIndex index of the {@code '{'} of the {@code ${}
     *                       being scanned
     * @return index of the matching {@code '}'}, or {@code -1} if none
     */
    private static int findExpressionEnd(String template, int openBraceIndex) {
        int depth = 0;
        char openQuote = 0;
        for (int i = openBraceIndex; i < template.length(); i++) {
            char current = template.charAt(i);
            if (openQuote != 0) {
                if (current == BACKSLASH) {
                    i++; // skip the escaped character (e.g. \' or \")
                } else if (current == openQuote) {
                    openQuote = 0;
                }
            } else if (current == SINGLE_QUOTE || current == DOUBLE_QUOTE) {
                openQuote = current;
            } else if (current == OPENING_BRACE) {
                depth++;
            } else if (current == CLOSING_BRACE) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
