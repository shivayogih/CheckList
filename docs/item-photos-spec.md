# Feature: item photos (requested by the project owner, verbatim)

Work in shivayogih/CheckList on branch feature/CL-210-item-photos from develop, commits like "CL-210 Add item photo storage", one PR into develop with a file change report and a verification report, as for the other phases.

## WHY
Users, especially older users and people sending a list to someone else to buy, want to attach a sample photo to an item so they remember exactly which one they need (the right brand of oil, the exact medicine strip, the shoe size label, the gift they saw). The app stays offline-first and free: no backend, no cloud upload.

## SCOPE (v1)
1. A checklist item can have 0 to 3 photos.
2. Photos come from either "Take photo" (camera) or "Choose from gallery".
3. Photos show as a small thumbnail on the item row, in a "Photos" section of the add/edit item form, and in a full-screen viewer.
4. Photos are copied with the item when a checklist is duplicated, and deleted when the item or checklist is deleted.
5. Export/import and PDF can optionally include photos.
Out of scope for now: photos on categories or master items, captions beyond one short optional line, cloud sync, AI reading photos.

## ANDROID / PRIVACY RULES
- Gallery: use the Android Photo Picker (ActivityResultContracts.PickVisualMedia / PickMultipleVisualMedia with ImageOnly, max = remaining slots). It needs no storage permission and is backported to minSdk 26 through Google Play services. Do NOT request READ_MEDIA_IMAGES or READ_EXTERNAL_STORAGE.
- Camera: use ActivityResultContracts.TakePicture with a FileProvider URI pointing to a temp file in cacheDir. Do NOT declare the CAMERA permission (declaring it would force a runtime prompt for the intent). Handle "no camera app" by hiding the option.
- Never store the picker's content:// URI; it can expire. Immediately copy the image into app-private storage: filesDir/item_photos/<uuid>.jpg.
- On copy: decode with downsampling, apply the EXIF orientation, scale so the long edge is at most 1600 px, re-encode JPEG quality 80, and write a 320 px thumbnail as <uuid>_t.jpg. Re-encoding strips EXIF, which removes GPS location; keep it that way and add a test that a GPS-tagged input produces a file with no location.
- Do all decoding/IO on Dispatchers.IO through an injected dispatcher; never on the main thread (StrictMode is on in dev).
- Photos are never sent to any AI service. Add this to the AI context-minimization rules (ChecklistContext stays unchanged) and to the privacy text in Settings.
- Exclude item_photos from Android Auto Backup only if the profile is already excluded the same way; otherwise include it. Follow whatever the existing backup rules file does and document the choice.

## DATA LAYER (:domain and :data)
- Domain model: ItemPhoto(id: PhotoId, itemId, fileName, width, height, byteSize, position, caption: String?, createdAt). ChecklistItem gains photos: List<ItemPhoto> (ordered by position).
- New Room entity/table item_photo: id TEXT PK (UUID), checklist_item_id TEXT FK -> checklist_item(id) ON DELETE CASCADE, file_name TEXT NOT NULL, width INT, height INT, byte_size INT, position INT (sparse, steps of 1000 like the rest of the schema), caption TEXT NULL (max 80 chars), created_at INT. Index (checklist_item_id, position).
- Bump CheckListDatabase to version 2 with an explicit Migration(1, 2) that only creates the table and index. Export schema 2.json and add a MigrationTestHelper test (v1 DB with data -> v2, all rows intact). No destructive fallback.
- The detail query keeps one @Transaction with @Relation, now including photos.
- PhotoStore interface in :domain (save(uri) -> StoredPhoto, delete(fileName), copy(fileName) -> newFileName, open(fileName)); Android implementation in :data using a FileSystem/Context-backed class so it can be unit tested with a temp dir.
- Use cases: AddItemPhotos (enforces max 3, returns how many were skipped), RemoveItemPhoto, ReorderItemPhoto (move left/right buttons, no drag required), SetPhotoCaption. Validation errors are typed, not strings.
- File cleanup: Room cascades only delete rows. After any item/checklist delete, delete the files in the same use case after the transaction commits. Also run an orphan sweep on app start in the background (files in item_photos with no row and older than 1 hour are deleted) so a crash can never leak storage.
- Duplicate checklist: copy each photo file to a new uuid and insert new rows, inside the duplicate flow.

## UI (:app, Compose, Material 3, existing theme)
- Item row: if the item has photos, show the first thumbnail at 48 dp square with 8 dp corners at the end of the row, before the overflow button, plus a "+2" badge when more exist. Tapping the thumbnail opens the viewer; tapping the rest of the row still toggles completion. Row stays at least 56 dp and still works at 200% font scale.
- Add/edit item form and the custom item form: a "Photos (optional)" section with up to 3 tiles (96 dp) and an "Add photo" tile that opens a small sheet with two large rows: "Take photo" and "Choose from gallery". Each tile has a visible remove button (labelled "Remove photo") and move left/right buttons; no long-press needed. Show "2 of 3 photos" text.
- Full-screen viewer: dark background, pinch and double-tap zoom, swipe or visible Previous/Next buttons, a visible "Delete photo" button with a confirm dialog ("Delete this photo?" / "Delete" / "Cancel"), and the optional caption field.
- While a photo is being processed show a placeholder tile with a progress indicator; on failure show "Couldn't add this photo. Try another one." next to the tiles.
- Accessibility: contentDescription "Photo 1 of 2 for Rice" (plus the caption when present) from string resources; thumbnails are decorative inside the merged row semantics except for an explicit "View photos" action; TalkBack can reach every photo button.
- Image loading: use Coil 3 (coil-compose) for thumbnails and the viewer, loading from the private file, with memory cache. Add it through the version catalog. If adding a library is a problem, decode thumbnails with BitmapFactory + LruCache instead.
- All new strings in values/ and all six translations (kn, hi, ta, te, mr, ml) in the same PR, with the same machine-assisted-draft comment the other translations carry. Plurals via <plurals>.

## IMPORT / EXPORT / PDF
- JSON export gains an "Include photos" switch, off by default. Off: same .json as today (formatVersion 1 readers unaffected; photos are simply dropped). On: export a .zip (MIME application/zip) containing checklists.json plus photos/<ref>.jpg; items carry "photos": [{ "ref": "p1", "file": "photos/p1.jpg", "caption": "..." }]. Bump formatVersion to 2 only for the zip variant; the importer accepts 1 and 2. Update docs/import-export-format.md and its golden-file tests.
- Import of a zip: validate before writing anything: max 3 photos per item, each entry <= 5 MB, total zip <= 100 MB, only .jpg/.jpeg/.png/.webp, reject path traversal ("../") and absolute paths, re-encode every image through the same pipeline as the camera/gallery path. Any failure rolls back the whole import and cleans up files already written. The preview shows "3 checklists, 42 items, 6 photos".
- PDF share sheet gains "Include photos" (off by default). On: draw each item's photos as small thumbnails (max 3, about 60 pt) under the item line, keeping the existing pagination, branding and watermark. Measure the size difference and report it.

## TESTS (must pass in GitHub Actions)
- Unit: AddItemPhotos limit, reorder math, PhotoStore save/delete/copy with a temp dir, EXIF location stripped, orientation applied, orphan sweep, duplicate copies files, delete removes files.
- Room: migration 1 -> 2 test; cascade delete test; detail query returns photos in order.
- Import/export: golden zip round trip; malicious zip cases (path traversal, oversize, wrong type, 4 photos on one item) all rejected with nothing written.
- Compose UI: item row with 0/1/3 photos, form add/remove buttons, viewer delete confirm; screenshot tests (Roborazzi) in light and dark and at 200% font scale, plus one Tamil locale.
- Run lint and the untranslated-strings check.

## DOCS
- docs/database.md: new table, migration, file storage rules.
- docs/import-export-format.md: zip variant and limits.
- Privacy notes: photos stay on the phone, never sent to AI, location removed.
- Add CL-210..CL-219 rows to the project-management CSVs like other features.

## DONE MEANS
Draft PR open into develop, CI green, the reports posted in the thread with screenshots of the item row, the form, the viewer and the PDF with photos, and a list of anything deferred.
