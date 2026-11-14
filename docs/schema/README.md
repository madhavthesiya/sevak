# Schema diagrams

The physical schema files here are **generated from the real database**. A test builds a fresh PostgreSQL with the Flyway migrations, reads the system catalog (`pg_class`, `pg_constraint`, `pg_index`, `information_schema.triggers`) and writes the files. Nothing is drawn by hand, so the diagrams can't fall behind the migrations.

| File | What it is |
|---|---|
| [`sevak-schema.svg`](sevak-schema.svg) | The physical schema on one page: all 29 tables, grouped and coloured by domain, with every column, key and foreign key |
| [`RELATIONAL_SCHEMA.md`](RELATIONAL_SCHEMA.md) | The course-style text schema (primary key underlined, foreign key in italics with `→`), with every UNIQUE, CHECK, exclusion constraint, index and trigger |
| [`sevak.dbml`](sevak.dbml) | The same schema in DBML. Paste it into [dbdiagram.io](https://dbdiagram.io) to move tables around |
| [`sevak-schema.dot`](sevak-schema.dot) | The Graphviz source of the SVG |
| [`CONCEPTUAL_MODEL.md`](CONCEPTUAL_MODEL.md) | The conceptual (Chen/EER) model: entities, relationships and cardinalities, checked against this schema |
| [`conceptual-er.svg`](conceptual-er.svg) | That model drawn in Chen/EER notation, also as [`conceptual-er.png`](conceptual-er.png) and an editable draw.io file, [`conceptual-er.drawio`](conceptual-er.drawio) |

The README's Mermaid diagram is generated the same way.

The conceptual diagram is the one exception. Its model is checked against the schema, but its layout is placed by hand in [`conceptual-er.mjs`](conceptual-er.mjs), so CI doesn't check it. After changing the model, run `node docs/schema/conceptual-er.mjs --png` (it uses Playwright from `web/node_modules` for the PNG).

## How CI keeps them honest

`SchemaDocsTest` runs in every `./mvnw verify`:

- It fails if any generated file differs from what the database says, for example after a new migration.
- It fails if a new table hasn't been given a domain (colour and cluster).
- The SVG's graph id carries a hash of the `.dot` file, so the test also fails if the picture wasn't redrawn after the `.dot` file changed.

## Regenerating after a schema change

Needs Docker. From the repository root:

```bash
# 1. Rewrite the text files from a freshly migrated database
cd api && ./mvnw test -Dtest=SchemaDocsTest -Dschema.docs.write=true -Dsurefire.failIfNoSpecifiedTests=false && cd ..

# 2. Redraw the SVG with Graphviz (no local install needed)
docker run --rm -v "$PWD/docs/schema:/work" -w /work alpine:3.20 \
  sh -c "apk add --no-cache graphviz font-dejavu >/dev/null && dot -Tsvg sevak-schema.dot -o sevak-schema.svg"
```

On Git Bash for Windows, put `MSYS_NO_PATHCONV=1` in front of `docker run` and use `$(pwd -W)` instead of `$PWD`.

## Reading the diagram

- **PK** primary key, **FK** foreign key, **UK** unique on its own, `*` NOT NULL.
- Lines run from the referencing table to the table it references; the `FK` badges show which columns hold the key. The end at the referencing table is a crow's foot (many) or a bar (at most one, when the foreign key is also unique). A circle means "zero is allowed".
- When a table has a composite foreign key and a single-column one to the same table (for example `booking_items` → `service_variants`), one line is drawn.
- The grey footer of each table lists its multi-column UNIQUE constraints, exclusion constraints, partial or unique indexes, and how many CHECKs and triggers it has. `RELATIONAL_SCHEMA.md` spells each one out.
