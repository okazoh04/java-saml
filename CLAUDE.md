# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is the **java-saml** toolkit (`com.onelogin:java-saml-toolkit`), a fork of OneLogin's SAML 2.0 library for Java (SP-side: SSO, SLO, assertion/NameID encryption, message signing, metadata publishing). It is a multi-module Maven project, targeting **Java 21** (`maven.compiler.release`), built with `jakarta.servlet` (not `javax.servlet`). Tests use **JUnit 6 (Jupiter)**, not JUnit 4 — use `org.junit.jupiter.api.Test`/`Assertions`/`assertThrows`, and `org.hamcrest.MatcherAssert.assertThat` for hamcrest matchers (JUnit 5+ dropped `Assert.assertThat`).

## Build / Test Commands

Run from the repo root (parent `pom.xml` aggregates all modules):

```bash
mvn clean package                          # build all modules (core, toolkit, samples)
mvn clean package -DskipTests              # skip tests
mvn clean verify                           # build + run OWASP dependency-check + tests
mvn clean install                          # install jars to local repo (needed before building toolkit if only core changed)
```

Run tests for a single module:
```bash
cd core && mvn test
cd toolkit && mvn test
```

Run a single test class:
```bash
mvn -pl core test -Dtest=AuthnResponseTest
mvn -pl toolkit test -Dtest=AuthTest
```

Notes:
- `core` and `toolkit` are independent Maven modules with their own `pom.xml`; `toolkit` depends on `core` (including its `test-jar` for shared test fixtures), so changes to `core` test utilities require `mvn install` on `core` before `toolkit` tests pick them up.
- `samples/java-saml-tookit-jspsample` is a JSP demo webapp, not exercised by the test suite — it's a manual reference for wiring the toolkit into a servlet container (Tomcat).
- The root `maven-enforcer-plugin` requires Java 21+ (`requireJavaVersion`); the old per-module check for unlimited-strength JCE (relevant only on JDK 8) was removed since unlimited cryptography is the JDK default since Java 9.
- `dependency-check-maven` (OWASP) runs on `verify` and fails the build on CVSS ≥ 7; suppressions live in `.nvd-suppressions.xml` at the repo root.

## Architecture

Three Maven modules, layered:

- **`core`** (`com.onelogin.saml2`, artifact `java-saml-core`) — the low-level, framework-agnostic SAML engine. No servlet dependency. Key packages:
  - `authn/` — `AuthnRequest` (builds outgoing SP→IdP requests) and `SamlResponse` (parses/validates incoming IdP responses; the largest and most security-sensitive class — signature, encryption, conditions, audience, and replay checks all live here)
  - `logout/` — `LogoutRequest` / `LogoutResponse`, each with a `*Params` companion class for building outgoing messages
  - `settings/` — `Saml2Settings` (in-memory settings model), `SettingsBuilder` (loads `onelogin.saml.properties`-style files into a `Saml2Settings`), `IdPMetadataParser` (fetch/parse IdP metadata XML into settings), `Metadata` (renders SP metadata XML)
  - `model/` — value objects (`Organization`, `Contact`, `KeyStoreSettings`, etc.) and `model/hsm/` for HSM-backed signing (currently `AzureKeyVault`)
  - `http/HttpRequest` — a framework-agnostic representation of an HTTP request, decoupling `core` from servlets; the `toolkit` module bridges real `HttpServletRequest`s into this
  - `util/` — `Util` (XML/crypto helpers), `Constants` (SAML URIs/NameID formats), `SchemaFactory` (XSD validation; schemas live in `core/src/main/resources/schemas`)
  - `exception/` — `SAMLException`, `SettingsException`, `ValidationError` (enumerated validation error codes used throughout `SamlResponse`/`LogoutRequest` validation)

- **`toolkit`** (`com.onelogin.saml2`, artifact `java-saml`) — the high-level, servlet-facing API:
  - `Auth` — the main entry point applications use. Stateful and **not thread-safe**; instantiate one per request. Loads settings via `SettingsBuilder` (default file `onelogin.saml.properties`, or a custom path), and exposes `login()`, `logout()`, `processResponse()`, `processSLO()`, metadata generation, and post-auth accessors (`getAttributes()`, `getNameId()`, `getSessionIndex()`, etc.)
  - `servlet/ServletUtils` — converts `jakarta.servlet.http.HttpServletRequest` into `core`'s `HttpRequest`, and handles redirects/responses back through `HttpServletResponse`
  - `factory/SamlMessageFactory` — interface with default methods that construct `AuthnRequest`/`SamlResponse`/`LogoutRequest`/`LogoutResponse` instances. `Auth` delegates object creation through this factory, so the extension point for custom message subclasses is implementing/overriding this factory and passing it to `Auth`, not subclassing `Auth` itself.

- **`samples`** — `java-saml-tookit-jspsample`, a JSP reference webapp showing SP-initiated SSO/SLO, ACS/SLS endpoints, and metadata publishing; settings live in `src/main/resources/onelogin.saml.properties`.

### Settings flow

`onelogin.saml.properties` (or an equivalent `Map`/`Properties`) → `SettingsBuilder` parses `onelogin.saml2.*` keys (SP/IdP entity IDs, endpoints, certs/private keys, security flags like `strict`, signature/digest algorithms, contacts/organization) → produces a `Saml2Settings` → consumed by `Auth`, `AuthnRequest`, `SamlResponse`, `LogoutRequest`/`LogoutResponse`, and `Metadata`. `IdPMetadataParser` can populate IdP-related settings directly from a fetched IdP metadata XML document instead of hand-editing properties.

### Test fixtures

`core/src/test/resources/config/` contains dozens of `config.*.properties` fixtures (e.g. `config.min.properties`, `config.allerrors.properties`, `config.hsm.properties`) used across tests to exercise different settings combinations; `core/src/test/resources/data/` holds canned SAML requests/responses/metadata XML (including pre-signed/encrypted variants) used to test parsing and validation without a live IdP. When adding tests for new settings or validation behavior, prefer adding/reusing a fixture in these directories over inlining XML/properties in test code.

## Security-sensitive areas

This is a security library — `SamlResponse` validation logic, signature/encryption handling in `Util`, and the `strict` setting in `Saml2Settings`/`SettingsBuilder` are the most safety-critical code paths (see README's "Security warning" section: `strict` must default to `true` in production, and `IdPMetadataParser` does not validate the URLs it's given, so callers handling untrusted IdP metadata URLs must guard against SSRF themselves). Treat changes in these areas with extra scrutiny and prefer adding/updating tests in `core/src/test/java/com/onelogin/saml2/test` over loosening validation.
