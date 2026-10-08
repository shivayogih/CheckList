# :data
Room database (the single source of truth), the bundled seed catalog and the Hilt bindings of the
domain repositories. DataStore settings, the encrypted profile, import/export and PDF arrive in later
phases. Depends on `:domain`.

- `local/entity`, `local/dao`, `local/database`: schema v1 (architecture section 5.3). Exported
  schemas live in `schemas/` and are committed; never use a destructive migration.
- `local/fts`: the multilingual item search index (section 6.6).
- `seed`: `SeedLoader` applies `src/main/assets/seed/catalog.json` and `i18n/<locale>.json` on
  database creation and whenever the asset's `seedVersion` grows.
- `repository`, `mapper`: `RoomChecklistRepository` and `RoomCatalogRepository`, with display-name
  resolution (section 6.2).
- `di`: `DataModule`.
