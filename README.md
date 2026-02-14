# NDC Clearance

**Live Site:** [ndc-clearance.netlify.app](https://ndc-clearance.netlify.app)  
**Public MCP Endpoint:** `https://mcp-ndc.sunrisehikers.io/mcp/sse`

NDC Clearance is an interactive browser, validator, and diff tool for IATA NDC schemas, with an MCP server for agent workflows.

![NDC Clearance Screenshot](screenshot.png)

## Repository Layout

- `site/`: Astro + Svelte static frontend
- `backend/`: Ktor backend (validation, diff API, MCP)
- `tools/`: Kotlin CLI tooling (flatten schemas, download examples, build content catalog)
- `ndc_schemas/`: generated flattened schemas
- `raw_ndc_schemas/`: original schema sources
- `ndc_content/`: canonical shared content for examples and future flows
- `iata_ndc_messages.json`: version/message map

## Shared `ndc_content` Architecture

Canonical content now lives at repo root and is shared by both `site` and `backend`.

```text
ndc_content/
  examples/
    files/iata/*.xml
    files/custom/*.xml
    sources/iata.generated.json
    sources/custom.json
    catalog.json
  flows/
    flows.json
```

- `sources/*.json`: editable/generated source records
- `catalog.json`: generated merged read model used by frontend and backend
- `flows.json`: flow definitions (structure and validation now in place; flow UX is out of scope)
- Example IDs are deterministic: `ex_<12 hex>` from  
  `source|message|version|source_page_id_or_url|file_name`
- Each example has a single `flow_id` (not `flow_ids`)
- For IATA records, flow is inferred automatically as one page = one flow (`flow_iata_page_<source_page_id>`)

## Local Development

### Prerequisites

- Node.js `24`
- Java `25`

### Frontend

```bash
cd site
npm ci
npm run dev
```

`dev` and `build` run `npm run copy-assets`, which executes:
- `npm run copy-schemas`
- `npm run copy-content` (copies `ndc_content/examples/**` to `site/public/content/examples/**`)

### Backend

```bash
cd backend
./gradlew run
```

Environment:
- `PORT` (default `8080`)
- `SCHEMA_ROOT` (default `src/main/resources/schemas`)
- `CONTENT_ROOT` (default `src/main/resources/content`)
- `POSTHOG_API_KEY` (optional)

### Tools

```bash
cd tools
./gradlew flatten
./gradlew download
./gradlew buildContentCatalog
./gradlew test
```

## Content Pipeline

### Refresh IATA examples

1. `cd tools && ./gradlew download`  
   Downloads from IATA pages directly into canonical `ndc_content/examples/files/iata/` and writes `ndc_content/examples/sources/iata.generated.json`.
2. `cd tools && ./gradlew buildContentCatalog`  
   Merges `iata.generated.json` + `custom.json`, validates flows/references, and emits `ndc_content/examples/catalog.json`.

### Add custom examples

1. Place XML in `ndc_content/examples/files/custom/`
2. Add record(s) in `ndc_content/examples/sources/custom.json`
3. Run `cd tools && ./gradlew buildContentCatalog`

### Add flow definitions (data only)

1. Edit `ndc_content/flows/flows.json` with steps referencing `example_id`
2. Run `cd tools && ./gradlew buildContentCatalog` to validate:
   - referenced `example_id` exists
   - step `message` matches referenced example message
   - duplicate flow IDs are rejected

## MCP Tools

Available at `/mcp/sse`:

- `validate_ndc_xml`
- `list_versions`
- `list_schemas`
- `get_schema_files`
- `list_examples` (optional: `message`, `version`; no filters returns all examples)
- `get_example_content` (required: `example_id`; optional: `include_metadata`, default `true`)

Examples/flows are exposed through MCP tools only. No REST endpoints were added for this content.

## Docker Compose

Run full stack locally:

```bash
docker-compose up --build
```

- Site: `http://localhost:4321`
- Backend: `http://localhost:8080`

## Disclaimer

This project is independent and is not affiliated with, endorsed by, or sponsored by IATA.

## License

AGPL
