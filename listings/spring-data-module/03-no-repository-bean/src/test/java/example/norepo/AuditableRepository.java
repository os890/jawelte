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
package example.norepo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Shared base interface — adds project-level convenience methods.
 * @NoRepositoryBean keeps spring-data-module's extension from
 * registering a synthetic bean for THIS interface; only concrete
 * subinterfaces ship as beans.
 */
@NoRepositoryBean
public interface AuditableRepository<T extends Auditable, ID> extends JpaRepository<T, ID> {

    long countAll();

    default boolean isEmpty() {
        return countAll() == 0;
    }
}
