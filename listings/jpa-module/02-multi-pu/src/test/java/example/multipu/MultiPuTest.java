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
package example.multipu;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * persistence.xml declares two units (notesPU + auditPU). jpa-module
 * publishes one synthetic EntityManager / EntityManagerFactory per PU,
 * each qualified @Named(puName). The same @Transactional method writes
 * to both PUs; each EM joins its own transaction; on commit both rows
 * are visible.
 */
@EnableTestBeans
class MultiPuTest {

    @Inject
    @Named("notesPU")
    EntityManager notesEm;

    @Inject
    @Named("auditPU")
    EntityManager auditEm;

    @Inject
    @Named("notesPU")
    EntityManagerFactory notesEmf;

    @Inject
    @Named("auditPU")
    EntityManagerFactory auditEmf;

    @Test
    void namedQualifierRoutesEachInjectionToItsOwnPu() {
        assertThat(notesEmf).isNotSameAs(auditEmf);
        assertThat(notesEm).isNotSameAs(auditEm);
    }

    @Test
    @Transactional
    void crossPuWriteCommitsBothPus() {
        notesEm.persist(new Note("hello"));
        auditEm.persist(new AuditRecord("note.created"));

        Long notesCount = notesEm.createQuery("SELECT COUNT(n) FROM Note n", Long.class).getSingleResult();
        Long auditCount = auditEm.createQuery("SELECT COUNT(a) FROM AuditRecord a", Long.class).getSingleResult();

        assertThat(notesCount).isEqualTo(1L);
        assertThat(auditCount).isEqualTo(1L);
    }
}
