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
