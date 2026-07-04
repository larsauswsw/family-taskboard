# CI/CD: Docker-Image-Publishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On every push to `main`, run the test suite, and — only if it passes — build the existing `Dockerfile` and publish the image to `ghcr.io/larsauswsw/family-taskboard`, tagged `latest` and with the short commit SHA.

**Architecture:** A single new GitHub Actions workflow file, `.github/workflows/docker-publish.yml`, with two sequential jobs: `test` (runs the Gradle test suite) and `build-and-push` (`needs: test`, builds and pushes via the standard `docker/*-action` toolchain using the built-in `GITHUB_TOKEN`, no new secrets).

**Tech Stack:** GitHub Actions, `docker/setup-buildx-action`, `docker/login-action`, `docker/metadata-action`, `docker/build-push-action` — no new application dependencies.

## Global Constraints

- Workflow triggers on `push` to `branches: [main]` only.
- Test job: Java 21 / Temurin, runs `./gradlew test integrationTest --rerun-tasks` (the `--rerun-tasks` flag is required for this project — a plain run undercounts due to Gradle's incremental test-task caching, per `.superpowers/sdd/progress.md`).
- Build-and-push job only runs if the test job succeeds (`needs: test`).
- Image tags: exactly `latest` and the short commit SHA (`type=raw,value=latest` + `type=sha,format=short` via `docker/metadata-action`).
- Platform: `linux/amd64` only — no multi-arch build.
- No new secrets — push authenticates via `secrets.GITHUB_TOKEN`, which requires the job to declare `permissions: packages: write`.
- Image name is `ghcr.io/${{ github.repository }}`, which resolves to `ghcr.io/larsauswsw/family-taskboard` — never hardcode the owner/repo string.
- This is infra/config work on a single YAML file — there is no TDD red/green cycle. Verification is: (1) a local YAML syntax check, (2) a real push that triggers the actual workflow, (3) confirming both jobs succeeded via `gh run view`, (4) confirming the image was actually published.
- Changing the GitHub Package's visibility from private to public is an access-control/permissions change on a shared resource — it must be done by the user themselves in the GitHub UI, never automated by an agent.

---

### Task 1: `docker-publish.yml` workflow — create, verify locally, push, confirm the real run succeeds

**Files:**
- Create: `.github/workflows/docker-publish.yml`

**Interfaces:**
- Consumes: the existing `Dockerfile` at the repo root (multi-stage, `eclipse-temurin:21-jdk` build stage running `./gradlew assemble`, `eclipse-temurin:21-jre` runtime stage) — unchanged, not modified by this task.
- Produces: nothing consumed by later tasks — this plan has only one task.

- [ ] **Step 1: Write the workflow file**

Create `.github/workflows/docker-publish.yml`:

```yaml
name: Build and Publish Docker Image

on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: gradle
      - name: Run tests
        run: ./gradlew test integrationTest --rerun-tasks

  build-and-push:
    needs: test
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/metadata-action@v5
        id: meta
        with:
          images: ghcr.io/${{ github.repository }}
          tags: |
            type=raw,value=latest
            type=sha,format=short
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          platforms: linux/amd64
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

- [ ] **Step 2: Verify the YAML is syntactically valid**

This repo has no YAML linter installed, but Ruby's standard library (already
present on this machine) can load and parse the file with zero extra
dependencies:

Run: `ruby -ryaml -e "puts YAML.load_file('.github/workflows/docker-publish.yml').inspect"`
Expected: prints a Ruby hash/array representation of the parsed document (e.g. starting with `{"name"=>"Build and Publish Docker Image", ...}`), no exception. A `Psych::SyntaxError` means the YAML is malformed — fix indentation/structure and re-run.

- [ ] **Step 3: Sanity-check the job names and trigger by eye**

Run: `cat .github/workflows/docker-publish.yml`
Confirm by reading the output:
- `on.push.branches` is exactly `[main]`.
- The `build-and-push` job has `needs: test`.
- The `build-and-push` job has `permissions.packages: write`.
- No hardcoded `larsauswsw/family-taskboard` string anywhere — only `${{ github.repository }}`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/docker-publish.yml
git commit -m "ci: build and publish Docker image to ghcr.io on push to main"
```

- [ ] **Step 5: Push to origin and trigger the real workflow run**

This is the first real end-to-end test of this workflow — there is no local
GitHub Actions runner available in this environment (`act` is not installed),
so this push IS the verification step. **Confirm with the user before
pushing**, per this project's established convention of asking before every
`git push`.

```bash
git push origin main
```

- [ ] **Step 6: Watch the triggered workflow run until it completes**

Run: `gh run list --workflow=docker-publish.yml --limit 1`
Note the run ID from the output, then:

Run: `gh run watch <run-id>` (replace `<run-id>` with the actual ID from the previous command)
Expected: the command streams job progress and exits once the run finishes. Both jobs (`test`, `build-and-push`) must show as successful (green checkmarks). If `test` fails, fix the underlying test failure (not the workflow file) and repeat from Step 4. If `build-and-push` fails, read `gh run view <run-id> --log-failed` for the failing step's output and fix the workflow file, then repeat from Step 2.

- [ ] **Step 7: Confirm the image was actually published with both tags**

Run: `gh api /users/larsauswsw/packages/container/family-taskboard/versions --jq '.[0].metadata.container.tags'`
Expected: a JSON array containing both `"latest"` and a 7-character short SHA matching the commit just pushed (check with `git rev-parse --short HEAD`).

- [ ] **Step 8: Manual step — flip the package to public (do not automate)**

GitHub creates a new container package as **private** by default, regardless
of the repository's visibility, and the `GITHUB_TOKEN` used by the workflow
has no permission to change this — it must be done by a human in the GitHub
UI, since this is an access-control/sharing-permission change on a shared
resource.

**Ask the user to do this themselves:** go to
`https://github.com/larsauswsw?tab=packages`, open the `family-taskboard`
package, click "Package settings", scroll to "Danger Zone", and change
visibility to "Public". This is a one-time step — it stays public for every
future push once set.

- [ ] **Step 9: Confirm anonymous pull works (after Step 8 is done)**

Run: `docker pull ghcr.io/larsauswsw/family-taskboard:latest`
Expected: the pull succeeds without any `docker login` — confirms the image
is genuinely public. If it fails with an authentication error, the package
is still private; ask the user to double-check Step 8 was completed and
saved.

- [ ] **Step 10: No further commit needed**

Step 1-4 already produced the only code change this plan requires. Steps 5-9
are verification against the real GitHub Actions/Packages infrastructure, not
additional file changes.
