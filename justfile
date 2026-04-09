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

# Run the full E2E suite: build → start container → run Playwright → stop container
e2e: build
    #!/usr/bin/env bash
    set -euo pipefail
    just e2e-up
    trap 'just e2e-down' EXIT
    cd e2e && pnpm test

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

# Run full build and e2e verification
ci-verify: build e2e
