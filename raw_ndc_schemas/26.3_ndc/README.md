# IATA NDC 26.3 sources

Imported from the user-supplied `26.3 Version XML & API Standards.zip`.

Archive SHA-256: `3955a87324981ffc5e13ad0ebfdb6f908957cabf808904afbd4e9bb527534d11`

The 51 XSD files are byte-for-byte originals: the 47 messages registered in
`iata_ndc_messages.json` plus their transitive dependencies:

- `IATA_OffersAndOrdersCommonTypes.xsd`
- `IATA_OffersAndOrdersCommonTypesFullyOptional.xsd`
- `IATA_PaymentClearanceCommonTypes.xsd`
- `xmldsig-core-schema.xsd`

The ZIP also contains unrelated XML standards, OpenAPI definitions and PDFs;
these are outside the NDC message set. It contains no worked XML examples.
Schema-internal IDs and version attributes are preserved as published; the
release version comes from the archive, not those attributes.

See the repository README for the reproducible `importSchemas` and `flatten`
commands. Generate flattened schemas locally in `ndc_schemas/26.3/` and commit
them together with these sources and the registered message list.
