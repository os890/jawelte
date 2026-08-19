# jawelte skill for agentic workflows

A [Claude Code](https://claude.com/claude-code) skill that teaches an agent how to set up
jawelte and write tests with it, so you do not have to re-explain the framework in every session.

## Install

Copy this folder into the consuming project (not into the jawelte repository itself):

```bash
cp -r skill <your-project>/.claude/skills/jawelte
```

Or for every project on the machine:

```bash
cp -r skill ~/.claude/skills/jawelte
```

The agent loads it automatically when a project depends on `org.os890.jawelte` artifacts, when a
test carries `@EnableTestBeans` / `@EnableJaxRs` / `@EnableWireMock` / `@EnableFlowAssert` /
`@TestControl`, or when such a test fails to start its container.

## Layout

`SKILL.md` is the always-loaded part: setup preconditions, the core pattern, and a table routing
to the rest. The files under `references/` are loaded only when that area is actually in play, so
a test that touches no database never pulls the persistence material into context.

| File | Covers |
| --- | --- |
| `references/setup.md` | dependencies, CDI runtime, required test resources, startup troubleshooting |
| `references/core-testing.md` | `@EnableTestBeans`, `@TestBean`, auto-mocking, scopes, `@ConfigBean` |
| `references/persistence.md` | JPA, JTA, data sources, JNDI, `@Resource`, migrations |
| `references/enterprise.md` | EJB session beans, Spring Data repositories, Jakarta Batch |
| `references/test-data.md` | `@TestControl`, `DbSeed`, `DbDiff` |
| `references/http-and-content.md` | `jaxrs-module`, `wiremock-module`, `content-diff-module` |
| `references/flow-assert.md` | recording and asserting CDI call flows |
| `references/configuration.md` | MicroProfile Config keys |

## Versioning

Written against jawelte **0.3.0**. The dependency coordinates and the minimal POM in
`references/setup.md` were verified by building and running a consumer project against the
published 0.3.0 artifacts from an empty local repository.
