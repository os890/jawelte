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
package org.os890.jawelte.tests.lnp.scenario09.service;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.os890.jawelte.tests.lnp.scenario09.entity.content.Article;

/** Content domain service — {@code @Stateless} EJB. */
@Stateless
public class ContentService {

    @Inject
    private EntityManager em;

    /** No-arg constructor required by the EJB stereotype. */
    public ContentService() {
    }

    /** Touch every Article — read-only query. */
    public void listArticles() {
        em.createQuery("SELECT a FROM Article a", Article.class)
                .getResultList();
    }

    /** Update article N's body. */
    public void updateArticleBody(Long id, String body) {
        Article art = em.find(Article.class, id);
        art.setBody(body);
        em.flush();
    }
}
