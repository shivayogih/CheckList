# Accessibility

CheckList is designed for people like Kamala, 68, who reads Kannada and uses only WhatsApp. If a screen works for her with large text and TalkBack, it works for everyone. Design reasoning: [phase0-architecture.md](phase0-architecture.md) section 10.

**Status:** the Phase 1 shell uses Material 3 type in `sp`, string-resource content descriptions and a visible back button rather than gestures. The Phase 3 screens (CL-132) apply the Compose rules below: merged checkbox rows with a state description, category headings, named "⋮" menus on every row, Move up/Move down in menus and as TalkBack actions, confirmation dialogs and Undo for item deletion, and auto-mirrored directional icons. The Phase 4 audit (CL-140 to CL-147, results below) added screen-title and dialog-title headings, titles that wrap at 200% text, per-language TalkBack voices for language names and a live region for the unit error, and it is now enforced by automated tests (Phase 7). Drag-to-reorder is not built; the in-app text size, the high-contrast theme and the Roborazzi screenshot matrix are still Planned.

## Targets

| Area | Requirement |
|---|---|
| Touch targets | At least 48dp everywhere; checklist item rows at least 56dp. The whole row toggles, not just the checkbox. |
| Text size | All sizes in `sp`. Usable at 200% system font scale. In-app "Text size" (Normal, Large, Extra large) multiplies on top of the system scale (Phase 4). |
| Contrast | WCAG 2.1 AA: at least 4.5:1 for text, 3:1 for UI components. High-contrast theme option (Phase 4). |
| Color | Never the only signal. Completed = tick + strikethrough + "completed" in semantics. |
| Screen readers | Full TalkBack navigation for every journey. |
| Gestures | No hidden gestures. Swipe-to-delete and drag-to-reorder may exist only as shortcuts; every action is also a visible button or menu item. |

## Compose rules

- **Item rows:** `Modifier.semantics(mergeDescendants = true)` so TalkBack reads one row as one item, with `stateDescription` for completion: "Rice, 5 kilograms, completed".
- **Icon buttons:** `contentDescription` from a string resource, describing the action ("Delete Goa Trip"), never `null` for an actionable icon. Decorative images use `contentDescription = null`.
- **Headings:** category section titles use `Modifier.semantics { heading() }` so users can jump between sections.
- **Progress:** expose `progressBarRangeInfo` on the progress bar, and a text equivalent ("3 of 8 done").
- **Reordering:** visible "Move up" and "Move down" buttons or menu items for categories and items.
- **Menus:** every checklist row has a visible "⋮" menu (Duplicate, Archive, Share, Delete). Long-press is never required.
- **Feedback:** snackbars with clear text, announced through a live region. Validation errors appear next to the field, not only as a toast.
- **Destructive actions:** a confirmation dialog with explicit verbs ("Delete 'Goa Trip'?" / "Delete" / "Cancel"), and Undo for item deletion.
- **Layouts:** no fixed heights for text; text wraps rather than truncates where the meaning matters.

## Plain language

Copy guide for every string (English first; translators follow the same tone):

| Write | Not |
|---|---|
| Add item | Insert entry |
| Delete | Remove permanently / Purge |
| Create checklist | New list instance |
| Can't connect. You can still add items yourself. | Network error (code 503) |
| Saved | Operation completed successfully |

- Short sentences, everyday words, no jargon, no abbreviations a first-time user would not know.
- Name the thing being acted on ("Delete 'Goa Trip'?").
- Tell the user what they can do next, especially in errors.

## Testing

| Check | How | Status |
|---|---|---|
| Semantics assertions | `ScreenAccessibilityTest` (CL-174) and the journey tests (CL-171) on Robolectric | Done |
| Automated accessibility checks | `assertAccessible()` in `app/src/test/.../testing/ui/AccessibilityAssertions.kt`: labels, 48dp targets, a heading, checkbox state, no clipped text | Done |
| 200% font scale | Every audited screen runs at font scale 1 and 2 with real text measurement (`@GraphicsMode(NATIVE)`) | Done |
| Palette contrast | `ThemeContrastTest` (CL-144): WCAG AA for the light and dark schemes, including inherited Material roles | Done |
| Screenshot matrix | Key screens × 7 languages × 100% and 200% font × light and dark (Roborazzi) | Planned (photos track adds Roborazzi) |
| Manual TalkBack script | Before each release, walk journeys J1 to J6 with TalkBack on and at 200% font | Planned (release checklist) |

### Audit results (Phase 4)

`ScreenAccessibilityTest` renders each screen with seeded data, at font scale 1 and 2, and runs `assertAccessible()`:

- every clickable or editable node has a label (text, content description or a merged child's text);
- every clickable node's touch bounds are at least 48dp × 48dp (Material's minimum interactive size counts);
- the screen has at least one heading;
- every checkbox with a toggle state also has a state description;
- no non-editable text reports visual overflow (clipped or ellipsised) at either scale.

| Screen | Found | Fix |
|---|---|---|
| All screens with a top bar | Title was not a heading; at 200% long titles clipped in the single-line bar | `AppTopBar`: heading title, two lines and a taller bar above 130% font scale |
| Dialogs (confirm, text input, unit picker, import preview) | Titles were not headings | `DialogTitle` marks them as headings |
| Home | Filter chips and the sort button overflowed the row at 200% | `FlowRow` wraps them |
| Home, first use | At 200% the Create checklist button was squeezed to 3px tall on a small screen | Content scrolls, and stays centred when it fits |
| Home | The floating Create checklist button had no label in the merged semantics | Content-slot `ExtendedFloatingActionButton`, whose text merges into the button |
| Add items | Clear search button in the field was 40dp at 200% | Explicit 48dp size |
| Language | TalkBack read "ಕನ್ನಡ" etc. with the current language's voice | Each name carries its own `LocaleList` span |
| Item editor | Unit error appeared silently | Polite live region |
| Counts ("3 of 8 done", "Add 2 items") | Not pluralised; Marathi showed Devanagari digits | `<plurals>` and `LocaleNumbers` (see [localization.md](localization.md)) |
| Detail, Settings | Only the shared top bar changed (attribute-level edits) | — |
| Palette | Light and dark text and UI pairs meet AA | No change needed |

Screens audited: Home (first use and with lists), Create checklist, Checklist detail (English and Kannada), Add categories, Add items with a selection, Item editor, Settings, Language and Profile. The AI command UI is owned by the AI track and is not part of this audit.

### Manual TalkBack script (outline)

1. Create a checklist from an empty Home screen (J1).
2. Add categories, then add two suggested items and one custom item with quantity and unit (J1, J2).
3. Tick an item and confirm the announcement includes the name, quantity and "completed".
4. Duplicate a checklist from the visible menu (J3).
5. Change the language to Kannada and back (J4).
6. Share a checklist as PDF (J5).
7. Move an item up and down without dragging.

Every step must be possible without sight and without gestures other than TalkBack's standard ones.
