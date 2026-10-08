# Rule engine schema 5

Schema 1–4 remains supported unchanged. Schema 5 adds generic technical capabilities, not external rule packs or publication text. Pack limits remain 500 rules, 3,000 tests and 5 MiB. Activation still requires passing tests and explicit rights confirmation.

## Literal comparisons

Use right = LITERAL and rightValue for a DOCUMENT_ field of kind BOOLEAN or TEXT, with EQ or NE. rightValue is required, typed according to the left field, and limited to 100 characters. It is forbidden for non-literal comparisons. Literal TestCase.right must be null: tests cannot override the configured expected value. Missing source facts remain NOT_EVALUABLE. Existing conditions and manual review behavior remain intact.

## Membership comparisons

IN and NOT_IN use a document TEXT field with right = LITERAL and parameters.values containing 1–20 unique nonempty strings (100 characters each). Values are compared with the existing whitespace/case normalization. rightValue may be omitted; if supplied it must equal the comma-joined values. The list is authoritative and appears as the expected value in findings. Missing facts never pass NOT_IN. Other unused parameter options are rejected.

## Context and document types

Additional explicit transport, signature, address, insurance, correction, invoice, certificate and packing facts are exposed through the existing tenant-scoped rule-facts forms. PEER_CONSIGNEE uses the uniquely selected peer document's reviewed consignee. New pairs are explicitly allowlisted, not arbitrary expressions. New document types: SEA_WAYBILL, CHARTER_PARTY_BILL_OF_LADING, MULTIMODAL_TRANSPORT_DOCUMENT, WEIGHT_LIST, POST_RECEIPT. These are selectable in the inbox, split review, upload/edit and requirement mapping, and have heading-based classification hints.

Fact objects support at most 192 fields within 64 KiB. Fields are never inferred to be false merely because they are missing. Source fact changes continue to use transactional audit and invalidate previous review decisions.

## Extension specifications

A JSON extension specification (specVersion plus rules, with optional status/requires annotations) may be previewed for read-only compatibility inspection. It is never stored or activated as a pack. The report separates unsupported capabilities from external bank-calendar configuration. It does not certify legal correctness or test coverage. Actual pack imports still require complete metadata, license/rights information, calendar definitions and independently reviewed test coverage.

Use schemaVersion 5 for generated packs using the new capabilities. Specification annotations such as status and requires must not be included in executable pack rules. No supplied private specifications, packs or accompanying documents belong in the public repository.
