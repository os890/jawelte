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
package example.scopemap;

import java.io.Serializable;

import jakarta.enterprise.context.SessionScoped;

/**
 * Production-shaped web-tier bean. {@code @SessionScoped} is the right
 * scope inside a servlet container — one instance per HTTP session —
 * but plain unit tests have no servlet session context, so the bean
 * is unusable as written. The {@code SessionToRequestScopedMapper}
 * on the test classpath rewrites the scope to {@code @RequestScoped}
 * at {@code ProcessAnnotatedType} time, and jawelte activates the
 * request context around every {@code @Test} method — so the same
 * production source becomes injectable in tests with no code change.
 */
@SessionScoped
public class UserPreferences implements Serializable {

    private static final long serialVersionUID = 1L;

    private String themeName = "default";

    public String getThemeName() {
        return themeName;
    }

    public void setThemeName(String themeName) {
        this.themeName = themeName;
    }
}
