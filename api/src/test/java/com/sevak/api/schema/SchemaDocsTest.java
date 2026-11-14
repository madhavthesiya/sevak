package com.sevak.api.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.sevak.api.IntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Keeps the schema documents in step with the migrations.
 *
 * <p>Normally it compares the committed files with what the live database says and fails if they
 * differ. Run it with {@code -Dschema.docs.write=true} to rewrite them instead.
 */
class SchemaDocsTest extends IntegrationTest {

    private static final Path DOCS = Path.of("..", "docs", "schema");
    private static final Path README = Path.of("..", "README.md");
    private static final String MERMAID_START = "<!-- schema:mermaid:start -->";
    private static final String MERMAID_END = "<!-- schema:mermaid:end -->";
    private static final boolean WRITE = Boolean.getBoolean("schema.docs.write");
    private static final String HOW_TO_FIX =
            " is out of date with the migrations. Regenerate it as described in docs/schema/README.md.";

    @Test
    void everyTableBelongsToExactlyOneDomain() {
        SchemaDocs.Schema schema = SchemaDocs.read(jdbc);
        Set<String> grouped = new HashSet<>();
        SchemaDocs.DOMAINS.forEach(d -> d.tables()
                .forEach(t ->
                        assertThat(grouped.add(t)).as("%s is in two domains", t).isTrue()));
        assertThat(grouped)
                .as("a new table needs a domain in SchemaDocs.DOMAINS")
                .containsExactlyInAnyOrderElementsOf(schema.tables().keySet());
    }

    @Test
    void schemaDocumentsMatchTheDatabase() throws IOException {
        SchemaDocs.Schema schema = SchemaDocs.read(jdbc);
        Map<String, String> files = new TreeMap<>();
        files.put("sevak.dbml", SchemaDocs.dbml(schema));
        files.put("sevak-schema.dot", SchemaDocs.dot(schema));
        files.put("RELATIONAL_SCHEMA.md", SchemaDocs.relational(schema));

        for (var file : files.entrySet()) {
            Path path = DOCS.resolve(file.getKey());
            if (WRITE) {
                Files.createDirectories(DOCS);
                Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
            } else {
                assertThat(read(path)).as(file.getKey() + HOW_TO_FIX).isEqualTo(file.getValue());
            }
        }
    }

    @Test
    void readmeDiagramMatchesTheDatabase() throws IOException {
        String readme = read(README);
        int start = readme.indexOf(MERMAID_START);
        int end = readme.indexOf(MERMAID_END);
        assertThat(start)
                .as("README.md needs the %s and %s markers", MERMAID_START, MERMAID_END)
                .isNotNegative()
                .isLessThan(end);
        String current = readme.substring(start + MERMAID_START.length(), end);
        String generated = "\n" + SchemaDocs.mermaid(SchemaDocs.read(jdbc));
        if (WRITE) {
            Files.writeString(
                    README,
                    readme.substring(0, start + MERMAID_START.length()) + generated + readme.substring(end),
                    StandardCharsets.UTF_8);
        } else {
            assertThat(current).as("The README's Mermaid diagram" + HOW_TO_FIX).isEqualTo(generated);
        }
    }

    @Test
    void theSvgWasRenderedFromTheCurrentDotFile() throws IOException {
        String dot = SchemaDocs.dot(SchemaDocs.read(jdbc));
        String id = dot.lines()
                .filter(l -> l.trim().startsWith("id=\"schema_"))
                .findFirst()
                .orElseThrow()
                .trim();
        String hash = id.substring("id=\"".length(), id.length() - 1);
        if (WRITE) {
            return; // the SVG is drawn from the new .dot file afterwards
        }
        assertThat(read(DOCS.resolve("sevak-schema.svg")))
                .as("sevak-schema.svg" + HOW_TO_FIX)
                .contains("id=\"" + hash + "\"");
    }

    static String read(Path path) throws IOException {
        if (!Files.exists(path)) {
            return "(missing file " + path + ")";
        }
        // A Windows checkout may turn \n into \r\n; the content is what matters.
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
