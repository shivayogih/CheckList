# :data
Room database (the single source of truth), the bundled seed catalog and the Hilt bindings of the
domain repositories, and the encrypted profile. DataStore settings, import/export and PDF arrive in
later phases. Depends on `:domain`.

- `local/entity`, `local/dao`, `local/database`: schema v1 (architecture section 5.3). Exported
  schemas live in `schemas/` and are committed; never use a destructive migration.
- `local/fts`: the multilingual item search index (section 6.6).
- `seed`: `SeedLoader` applies `src/main/assets/seed/catalog.json` and `i18n/<locale>.json` on
  database creation and whenever the asset's `seedVersion` grows.
- `repository`, `mapper`: `RoomChecklistRepository` and `RoomCatalogRepository`, with display-name
  resolution (section 6.2).
- `importexport` (Phase 6): `JsonTransferCodec` (the versioned JSON file of
  docs/import-export-format.md, with parse-time limits) and `RoomTransactionRunner` (the one
  transaction an import writes in). The pipeline itself is in `:domain` (`domain/transfer`).
- `profile`: the encrypted profile (Phase 5). `KeysetProfileAeadProvider` holds a Tink AES-256-GCM
  keyset wrapped by an Android Keystore key, `ProfileCipher` encrypts the row with associated data
  bound to table, column, row, version and key alias, and `EncryptedProfileRepository` handles key
  loss and tampering. Details: [docs/security.md](../docs/security.md).
- `di`: `DataModule`, `ProfileModule`, `ImportExportModule`.
