# Keyboard and window insets (CL-300..304)

Rule: when the soft keyboard opens, no text field, error text, counter or primary action may be
hidden, overlapped or out of reach, and every form must stay scrollable.

## Root cause of the bug report

"When the user types, the input layout is hidden and the page cannot be scrolled."

`MainActivity` calls `enableEdgeToEdge()` and the app targets Android 15+ (targetSdk 36). In
edge-to-edge mode the window no longer resizes for the keyboard, whatever `windowSoftInputMode` says;
the keyboard is only a window inset (`WindowInsets.ime`). Compose moves nothing out of its way unless
the layout consumes that inset. Before this change only two screens did (item editor bottom bar and
first-run profile setup), so on the others the keyboard sat on top of the lower half of the form and
the scrollable area still extended under it, which is why content could not be reached.

The manifest keeps `android:windowSoftInputMode="adjustResize"`. It is harmless in edge-to-edge and
is the right setting if the app is ever run without edge-to-edge; the inset handling below does not
depend on it.

## The helper set

All in `presentation/common/KeyboardInsets.kt`. Screens use these and nothing else; do not add ad hoc
`imePadding()` calls.

| Helper | Where | What it does |
|---|---|---|
| `Modifier.keyboardAwareScreen()` | `Scaffold(modifier = ...)` of every screen | `imePadding()` at the root. The whole Scaffold (content, bottom bar, snackbar) shrinks to the space above the keyboard. It consumes the inset the navigation bar is part of, so the bottom bar's own `navigationBarsPadding()` drops to 0 while the keyboard is open: no double padding. |
| `Modifier.scrollableForm()` | the `Column` of a form | `verticalScroll` plus `dismissKeyboardOnOutsideInteraction()`. Put it before inner padding. |
| `Modifier.dismissKeyboardOnOutsideInteraction()` | a `LazyColumn` or any scroll container | Dragging the content hides the keyboard (nested scroll, user input only). Tapping empty space clears focus. Taps on fields and buttons are unaffected because they consume the tap first. |
| `Modifier.bringIntoViewWhenFocused()` | a text field, or a column holding a field and its helper text | Reveals the whole block (field, error text, counter) when it gains focus and again once the keyboard has finished opening (300 ms), because the viewport shrinks while the keyboard animates. |
| `rememberDismissKeyboardActions()` | `keyboardActions` of search fields and last fields | Done, Search and Go close the keyboard and clear focus. Next already moves focus by default. |
| `Modifier.keyboardAwareSheet()` | root column of a `ModalBottomSheet` | Sheets live in their own window, outside the Scaffold: navigation bar padding then IME padding. |
| `ResizeDialogForKeyboard()` | inside a dialog's content | Sets the dialog window to `SOFT_INPUT_ADJUST_RESIZE` so it re-centres above the keyboard. |

Shared components apply them already: `FormField` (bring into view), `SearchField` (Search closes the keyboard), `BottomActionBar` (no inset of its own), `AppModalBottomSheet` (`keyboardAwareSheet()`), `TextInputDialog` (resize,
scrollable content, bring into view, Done confirms when the button is enabled).

### Adding a new form screen

```kotlin
Scaffold(
    modifier = Modifier.keyboardAwareScreen(),
    bottomBar = { /* BottomActionBar or Button with navigationBarsPadding(), no imePadding() */ },
) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).scrollableForm().padding(16.dp)) {
        FormField(...)                   // or OutlinedTextField(... .bringIntoViewWhenFocused())
    }
}
```

For a list, use `LazyColumn(Modifier.fillMaxSize().padding(padding).dismissKeyboardOnOutsideInteraction())`.
Set `ImeAction.Next` on every field but the last; the last uses `Done` (single line) or the default
(multi-line, where Enter is a new line). Search fields use `ImeAction.Search` with
`rememberDismissKeyboardActions()`.

## Screen audit

"Before" is the behaviour on Android 15+ edge-to-edge as found in the code audit.

| Screen / dialog | Text fields | Before | After |
|---|---|---|---|
| Home | search | list bottom under the keyboard and not reachable; Search key did nothing | Scaffold lifts, list scrolls to its end, drag hides keyboard, Search closes keyboard |
| Create checklist | title, description, new-category dialog | Create button and description under the keyboard; list could not scroll past it | Scaffold lifts (button above keyboard), fields reveal with counter and error, drag/tap dismisses |
| Checklist detail | AI command field (when on), rename dialog | AI panel and last items under the keyboard | Scaffold lifts, panel revealed on focus, drag dismisses; rename uses the dialog rules |
| Item editor | name, quantity, notes, new-unit dialog | bar lifted by its own `imePadding` but content behind it not resized (notes/counter hidden); Notes could be out of reach | Root lift replaces the bar-level padding, form scrolls, each field reveals with error/counter, Next/Next/default |
| Add items | search, per-item quantity | quantity of a selected item and the action bar under the keyboard | Scaffold lifts, quantity reveals on focus, Search and Done close the keyboard |
| Add categories | new-category dialog | dialog could clip, list under the keyboard | Scaffold lifts, shared dialog rules |
| Profile (Settings) | name, email, phone, address | no inset handling; Save and address hidden, snackbar under keyboard | Scaffold lifts (snackbar too), form scrolls, fields reveal |
| First-run profile setup | name, phone, email, address | already lifted (own `imePadding`), but not dismissible | same helper as the rest, scrollable form dismisses on drag/tap |
| Text input dialog (rename, category, unit) | one field + extras | content not scrollable, window left to platform default | content scrolls, window resizes, field reveals, Done confirms |
| AI review sheet | none | no field; already scrollable | unchanged (no keyboard) |
| Import / export (Settings), import preview dialog | none (system file pickers) | n/a | unchanged; preview content already scrolls |
| Language, Welcome, Tutorial | none | n/a | unchanged |

## What the tests prove, and what they cannot

`KeyboardInsetsTest` and `KeyboardScreensTest` (Robolectric, CL-171 harness) deliver IME window insets
to the Compose view (`testing/ui/ImeSimulation.kt`) and assert on layout:

- a control: without the helper the bottom bar stays at the screen bottom (so the simulation is real);
- with the helper the bar lifts above the keyboard and drops back when it closes;
- a long form has a scroll container and its last field can be scrolled to above the keyboard;
- a field that gains focus off screen is scrolled into view;
- tapping empty space and the Search key clear focus;
- every form screen (create, item editor, add items, add categories, profile, detail, Home) has its
  primary action above the keyboard and a scroll container.

Robolectric cannot prove: the real keyboard animation and timing, how a given keyboard app reports its
height (emoji panels, floating or split keyboards, gesture navigation), the system's choice to pan or
resize a dialog window, `SOFT_INPUT_ADJUST_RESIZE` on the dialog window, or hardware-keyboard
behaviour. Those need an emulator or device.

### Manual check on an emulator (API 35/36, 3-button and gesture navigation)

1. For every row in the audit table, focus each field: it, its error text and its counter are visible.
2. Type until an error and the counter appear on the last field: still visible.
3. Scroll with the keyboard open; dragging the form hides the keyboard.
4. Bottom button is above the keyboard and has no extra gap equal to the navigation bar.
5. Rename and new-category dialogs: usable in portrait and landscape and at 200% font.
6. Repeat once with a floating keyboard and with Hindi/Tamil keyboards (taller candidate bars).

## Deferred

- `imeNestedScroll()` (keyboard follows the finger) is experimental; not used.
- Edge-to-edge ownership of the dialog window (`decorFitsSystemWindows = false`) is not needed while
  the dialog resizes; revisit if the UI foundation track restyles dialogs.
