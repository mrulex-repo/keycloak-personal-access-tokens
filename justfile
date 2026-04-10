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
    VERSION=$(git tag --list 'v*.*.*' --sort=-version:refname | head -1 | sed 's/^v//')
    if [[ -z "$VERSION" ]]; then
        echo "No release tag found. Run 'just release' first."
        exit 1
    fi
    cd extension && ./gradlew shadowJar
    mkdir -p ../build
    cp "build/libs/keycloak-personal-access-tokens-${VERSION}.jar" \
       "../build/keycloak-personal-access-tokens-${VERSION}.jar"
    echo "Extension JAR: build/keycloak-personal-access-tokens-${VERSION}.jar"

# Copy the theme JAR to build/
package-theme:
    #!/usr/bin/env bash
    set -euo pipefail
    VERSION=$(git tag --list 'v*.*.*' --sort=-version:refname | head -1 | sed 's/^v//')
    if [[ -z "$VERSION" ]]; then
        echo "No release tag found. Run 'just release' first."
        exit 1
    fi
    mkdir -p build
    cp theme/dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar \
       "build/keycloak-personal-access-tokens-theme-${VERSION}.jar"
    echo "Theme JAR: build/keycloak-personal-access-tokens-theme-${VERSION}.jar"

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
# Release
# ---------------------------------------------------------------------------

# Analyse commits since the last release tag, bump the semver version in both
# extension/build.gradle.kts and theme/package.json, commit, and create a git tag.
# Bump rules (highest wins): breaking/type! → major | feat/feature → minor | else → patch
release:
    #!/usr/bin/env bash
    set -euo pipefail

    LAST_TAG=$(git tag --list 'v*.*.*' --sort=-version:refname | head -1)

    if [[ -z "$LAST_TAG" ]]; then
        echo "No release tag found — treating as v0.0.0"
        LAST_TAG="v0.0.0"
        COMMITS=$(git log --pretty=format:"%s")
    else
        echo "Last release tag: $LAST_TAG"
        COMMITS=$(git log "${LAST_TAG}..HEAD" --pretty=format:"%s")
    fi

    if [[ -z "$COMMITS" ]]; then
        echo "No commits since $LAST_TAG — nothing to release."
        exit 0
    fi

    # Parse current semver
    VERSION=${LAST_TAG#v}
    MAJOR=$(echo "$VERSION" | cut -d. -f1)
    MINOR=$(echo "$VERSION" | cut -d. -f2)
    PATCH=$(echo "$VERSION" | cut -d. -f3)

    # Walk commits and determine the required bump level
    BUMP="patch"
    while IFS= read -r msg; do
        # Breaking: any conventional-commit type with ! (e.g. feat!:, fix!:)
        # or explicit break/breaking prefix
        if echo "$msg" | grep -qE '^[a-z]+(\([^)]+\))?!:'; then
            BUMP="major"; break
        elif echo "$msg" | grep -qiE '^(break|breaking)(\([^)]+\))?:'; then
            BUMP="major"; break
        elif echo "$msg" | grep -qiE '^(feat|feature)(\([^)]+\))?:'; then
            [[ "$BUMP" != "major" ]] && BUMP="minor"
        fi
    done <<< "$COMMITS"

    # Compute next version
    case "$BUMP" in
        major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
        minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
        patch) PATCH=$((PATCH + 1)) ;;
    esac

    NEXT="${MAJOR}.${MINOR}.${PATCH}"
    echo "Bump: $LAST_TAG → v${NEXT}  (${BUMP})"

    # Update extension/build.gradle.kts
    sed -i "s/^version = \"[^\"]*\"/version = \"${NEXT}\"/" extension/build.gradle.kts

    # Update theme/package.json  (match the "version": "..." line)
    sed -i "s/\"version\": \"[^\"]*\"/\"version\": \"${NEXT}\"/" theme/package.json

    # Commit version files and tag
    git add extension/build.gradle.kts theme/package.json
    git commit -m "chore: release v${NEXT}"
    git tag "v${NEXT}"

    echo "Released v${NEXT} — push the tag with: git push origin v${NEXT}"

# Publish build artifacts for the current git tag to GitHub Releases.
# Requires GITHUB_TOKEN env var. Reads GITHUB_REPO (owner/repo) from the git
# remote origin, or override by setting the env var explicitly.
publish:
    #!/usr/bin/env bash
    set -euo pipefail

    : "${GITHUB_TOKEN:?GITHUB_TOKEN is required}"

    TAG=$(git tag --list 'v*.*.*' --sort=-version:refname | head -1)
    if [[ -z "$TAG" ]]; then
        echo "No release tag found. Run 'just release' first."
        exit 1
    fi
    VERSION=${TAG#v}

    EXT_JAR="build/keycloak-personal-access-tokens-${VERSION}.jar"
    THEME_JAR="build/keycloak-personal-access-tokens-theme-${VERSION}.jar"

    [[ -f "$EXT_JAR" ]]   || { echo "Missing $EXT_JAR — run 'just package' first."; exit 1; }
    [[ -f "$THEME_JAR" ]] || { echo "Missing $THEME_JAR — run 'just package' first."; exit 1; }

    # Derive owner/repo from remote origin, allow env override
    REPO="${GITHUB_REPO:-$(git remote get-url origin | sed -E 's#.*github\.com[/:](.+/.+)\.git#\1#; s#.*github\.com[/:](.+/.+)#\1#')}"
    API="https://api.github.com/repos/${REPO}"

    echo "Publishing ${TAG} to ${REPO}..."

    # 1. Create the release and capture the upload URL
    RELEASE_JSON=$(curl -fsSL -X POST "${API}/releases" \
        -H "Authorization: Bearer ${GITHUB_TOKEN}" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        -d "{\"tag_name\":\"${TAG}\",\"name\":\"Release ${TAG}\",\"generate_release_notes\":true}")

    UPLOAD_URL=$(echo "$RELEASE_JSON" | grep -o '"upload_url":"[^"]*"' | cut -d'"' -f4 | sed 's/{?name,label}//')
    RELEASE_URL=$(echo "$RELEASE_JSON" | grep -o '"html_url":"[^"]*"' | head -1 | cut -d'"' -f4)

    if [[ -z "$UPLOAD_URL" ]]; then
        echo "Failed to create release. Response:"
        echo "$RELEASE_JSON"
        exit 1
    fi

    # 2. Upload artifacts
    _upload() {
        local file="$1" name
        name=$(basename "$file")
        echo "  Uploading ${name}..."
        curl -fsSL -X POST "${UPLOAD_URL}?name=${name}" \
            -H "Authorization: Bearer ${GITHUB_TOKEN}" \
            -H "Accept: application/vnd.github+json" \
            -H "X-GitHub-Api-Version: 2022-11-28" \
            -H "Content-Type: application/java-archive" \
            --data-binary "@${file}" > /dev/null
    }

    _upload "$EXT_JAR"
    _upload "$THEME_JAR"

    echo "Done: ${RELEASE_URL}"

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
