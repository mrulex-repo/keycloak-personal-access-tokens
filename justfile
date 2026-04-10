# Keycloak Personal Access Tokens — build orchestration

# List available recipes
default:
    @just --list

# Build extension and theme (quality checks + compile)
build: build-extension build-theme

# Build the Keycloak extension: format, lint, checkstyle, test, and compile
build-extension:
    cd extension && ./gradlew rewriteRun spotlessApply checkstyleMain checkstyleTest build jacocoTestCoverageVerification

# Build the Account UI theme: lint, test, compile, and keycloakify build
build-theme:
    cd theme && pnpm run lint:fix && pnpm run test && pnpm run build-keycloak-theme

# Create deployable artifacts for extension and theme
package: package-extension package-theme

# Create the extension shadow JAR and copy it to build/
package-extension:
    #!/usr/bin/env bash
    set -euo pipefail
    cd extension && ./gradlew shadowJar
    mkdir -p build
    cp extension/build/libs/keycloak-personal-access-tokens-1.0.0.jar build/
    echo "Extension JAR: build/keycloak-personal-access-tokens-1.0.0.jar"

# Copy the theme JAR to build/
package-theme:
    #!/usr/bin/env bash
    set -euo pipefail
    mkdir -p build
    cp theme/dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar \
       build/keycloak-personal-access-tokens-theme.jar
    echo "Theme JAR: build/keycloak-personal-access-tokens-theme.jar"

# Clean all build and package outputs
clean: clean-extension clean-theme
    rm -rf build

# Clean extension build and package outputs
clean-extension:
    cd extension && ./gradlew clean

# Clean theme build and package outputs
clean-theme:
    rm -rf theme/dist theme/dist_keycloak

# ---------------------------------------------------------------------------
# E2E tests (Playwright + Docker)
# ---------------------------------------------------------------------------

# Start container, run Playwright, stop container (build must have run first)
e2e-run:
    #!/usr/bin/env bash
    set -euo pipefail
    just e2e-up
    trap 'just e2e-down' EXIT
    cd e2e && pnpm test

# Run the full E2E suite: build → start container → run Playwright → stop container
e2e: build e2e-run

# Stop the E2E Keycloak container
e2e-down:
    cd e2e && docker compose down

# Install Playwright browsers (run once after pnpm install)
e2e-install:
    cd e2e && pnpm install && pnpm run install-browsers

# Start the E2E Keycloak container (requires `just build` to have run first)
e2e-up:
    #!/usr/bin/env bash
    set -euo pipefail
    cd e2e
    docker compose up -d
    echo "Waiting for Keycloak E2E container to be ready..."
    for i in $(seq 1 90); do
      if ! docker compose ps keycloak | grep -q "Up\|running"; then
        echo "Container exited unexpectedly:"
        docker compose logs keycloak | tail -30
        exit 1
      fi
      if curl -sf http://localhost:18080/realms/test > /dev/null 2>&1; then
        echo "Keycloak is ready at http://localhost:18080"
        # Warm up theme templates before tests start. The realm endpoint responds while
        # Keycloak is still compiling Freemarker templates on first access. Following the
        # account → login redirect forces template compilation so the first browser login
        # does not hit cold-start latency (which can exceed the 30 s waitFor timeout).
        curl -sfL "http://localhost:18080/realms/test/account/" -o /dev/null 2>&1 || true
        exit 0
      fi
      printf "  [%ds] waiting...\r" "$((i * 2))"
      sleep 2
    done
    echo "Timed out after 180s. Container logs:"
    docker compose logs keycloak | tail -50
    exit 1

# ---------------------------------------------------------------------------
# CI
# ---------------------------------------------------------------------------

# Run full verification (build + e2e) then package artifacts
ci: ci-verify package

# Run full build and e2e verification, then print a timing + artifact summary
ci-verify:
    #!/usr/bin/env bash
    set -euo pipefail
    t0=$(date +%s)

    just build
    t_build=$(date +%s)

    just e2e-run
    t_e2e=$(date +%s)

    echo ""
    echo "══════════════════════════════════════════════════════"
    echo "  ci-verify complete"
    echo "══════════════════════════════════════════════════════"
    printf "  %-12s %d s\n" "build"   "$((t_build - t0))"
    printf "  %-12s %d s\n" "e2e"     "$((t_e2e  - t_build))"
    printf "  %-12s %d s\n" "total"   "$((t_e2e  - t0))"
    echo ""
    echo "  Reports"
    echo "  ├─ extension/build/reports/tests/test/index.html"
    echo "  ├─ extension/build/reports/checkstyle/"
    echo "  ├─ extension/build/reports/spotbugs/"
    echo "  ├─ extension/build/reports/jacoco/test/html/index.html"
    echo "  └─ e2e/playwright-report/index.html"
    echo ""
    echo "  Artifacts"
    echo "  └─ theme/dist_keycloak/"
    echo "══════════════════════════════════════════════════════"
