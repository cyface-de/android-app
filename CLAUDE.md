# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The README is the main documentation. It describes the installation, the secrets, the modules, the development commands, the workflows and the release:

@README.adoc

## Rules for Changes

- Keep the README current. When you change a module, a property in `gradle.properties`, a command or a workflow, update the README in the same commit.
- `backend/`, `energy_settings/` and `camera_service/` are submodules with their own repositories. Change them there, then update the submodule commit and the version in the root `build.gradle` together.
- Debug builds use the staging or demo APIs, release builds the production APIs. Never commit `gradle.properties` or `sentry.properties`.
- Follow the rules of the workflows in the README section "GitHub Workflows" for each change of `.github/workflows/`.

## Commit Style

`[TICKET-ID] Short imperative summary`, for example `[LEIP-410] Fix Digural API URL not updating without app restart`.
