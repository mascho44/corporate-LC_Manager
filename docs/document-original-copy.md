# Original / numbered copy designation

Documents can be explicitly marked **Original**, **Copy 1**, **Copy 2**, **Copy 3**, or left **Not designated**. This is user-entered presentation metadata, not proof of legal originality/authenticity and not an automatic count/compliance finding.

Choose the designation per file during LC batch upload, per part in split review, or while assigning an inbox document to an LC. It is displayed in the document dossier and editable with the existing document metadata editor. ZIP entries remain unspecified and can be edited individually after import. Historical documents and automatically split items remain unspecified. Generated documents likewise are not automatically declared originals.

The `copyNumber` API field is null for unspecified, 0 for original, and 1–3 for numbered copies. Both application and PostgreSQL enforce the range. Classification/split training deliberately discards this designation: a template must not label future documents original merely because one training example was.
