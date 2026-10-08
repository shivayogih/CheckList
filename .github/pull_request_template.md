<!--
Title format: CL-xxx Imperative summary (for example "CL-121 Add checklist creation flow").
One issue per PR. Feature PRs target `develop`; release fixes target `release/x.y.z`.
Delete the hints you do not need, but keep every heading.
-->

## Summary

**Before:** <!-- What the app or repository did before this change. -->

**After:** <!-- What it does now, from the user's or developer's point of view. -->

## Issue

CL-xxx <!-- The issue this PR closes. Update its row in docs/project-management/issues.csv. -->

## How

<!-- The approach and the main files touched. Mention any design decision that changed, and its ADR. -->

## Testing

<!-- Commands run and their result, for example:
./gradlew :domain:test :app:testDevDebugUnitTest
./gradlew :app:lintDevDebug
Manual checks: device or emulator, Android version, font scale, theme, language. -->

## Screenshots (UI changes)

<!-- Before/after screenshots. For UI changes include at least one Indic language and 200% font scale.
Write "Not applicable" when nothing visible changed. -->

## Checklist (Definition of Done, docs/phase0-architecture.md section 24)

- [ ] Layers respected: `:domain` stays pure Kotlin, `:ai` does not depend on `:data`
- [ ] Unit tests (and Room/UI tests where relevant) added and passing; no skipped or disabled tests
- [ ] UI checked at 100% and 200% font scale, light and dark (or not applicable)
- [ ] Lint passes with no new baseline entries (detekt and ktlint as well once CL-106 lands)
- [ ] Localization: no hard-coded user-visible text; strings present in all 7 languages (en, kn, hi, ta, te, mr, ml) or a tracked follow-up issue
- [ ] Accessibility: content descriptions, 48dp touch targets, contrast and the TalkBack path checked (or not applicable)
- [ ] Security and privacy: no secrets; no personal data in logs, exports or AI context; new permissions justified
- [ ] Documentation updated (relevant `docs/*.md`, plus an ADR if a decision changed)
- [ ] Commits follow `CL-xxx Imperative summary`
- [ ] `docs/project-management/issues.csv` updated (status, branch, PR)
- [ ] CI green; AI review findings addressed or answered (from Phase 11)
