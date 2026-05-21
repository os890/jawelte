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
package example.sessionremap;

import jakarta.enterprise.context.SessionScoped;

import java.io.Serializable;

/**
 * A typical web-tier bean declared @SessionScoped. In production it
 * holds per-HTTP-session state. In a CDI test there is no servlet
 * session — scope-module's BeanScopeMapper rewrites it to
 * @TestMethodScoped at ProcessAnnotatedType time, so the bean
 * behaves as method-scoped without any source change.
 */
@SessionScoped
public class UserPreferences implements Serializable {

    private String themeName = "default";

    public String getThemeName() {
        return themeName;
    }

    public void setThemeName(String themeName) {
        this.themeName = themeName;
    }
}
