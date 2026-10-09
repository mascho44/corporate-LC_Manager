# Document review workflow

## Inbox metadata review

Use **Bearbeitungsstand speichern** to persist corrected metadata, the selected
LC case, document type, original/copy designation and layout profile. This saves
a draft without training. Reopening the inbox restores the saved values,
including explicitly cleared values. Unsaved edits remain local to the browser.

**Metadaten bestätigen & lernen** additionally records the reviewed metadata as
a tenant-specific training confirmation. Saving and confirmation are audited.
LC master data is not changed by either operation.

## Learned layout profiles

Administrators with SETTINGS_MANAGE can open **Layoutprofile verwalten** in the
inbox. Each retained confirmation can be renamed or deactivated. Only active
confirmations participate in suggestions. Multiple distinct active patterns
under the same profile are marked as conflicting; no ambiguous suggestion is
applied. Resolve the conflict by disabling invalid confirmations or separating
them into differently named profiles. Changes are tenant-scoped and audited.

## Missing document facts

The document rule-facts dialog offers a filter for missing supplementary facts
referenced by active packs for that document type. This is a collection of
possible rule inputs, not a declaration that all fields are mandatory or that
every rule applies. Existing values remain in the form and are retained when
saving a filtered view. Basic metadata is corrected separately in the document.

## Identical files

The inbox and LC cockpit offer an explicit, read-only byte-content comparison.
SHA-256 equality indicates identical files, not authenticity or equivalence of
physical originals. Original/copy designations are displayed individually.
Nothing is automatically merged or deleted. Checks cover at most 100 files and
100 MB of hashed content; a limited report is marked accordingly.
