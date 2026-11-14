package com.sevak.api.schema;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads the real schema from the PostgreSQL catalog and writes it out as diagrams and text.
 *
 * <p>Nothing here is typed in by hand except the domain grouping, the colours and the README
 * relationship labels. Every table, column, key, constraint, index and trigger comes from the
 * database that the Flyway migrations built, so the documents can't drift from the migrations.
 */
final class SchemaDocs {

    static final String SCHEMA = "sevak";

    /** One domain per cluster of the conceptual ER diagram (roadmap task 7.1). */
    record Domain(String key, String title, String colour, String fill, List<String> tables) {}

    static final List<Domain> DOMAINS = List.of(
            new Domain(
                    "identity",
                    "Identity",
                    "#2563EB",
                    "#EFF6FF",
                    List.of("users", "customers", "service_providers", "admins", "admin_permissions")),
            new Domain(
                    "location",
                    "Geography",
                    "#059669",
                    "#ECFDF5",
                    List.of("cities", "areas", "locations", "customer_addresses", "provider_service_areas")),
            new Domain(
                    "catalog",
                    "Catalog",
                    "#D97706",
                    "#FFFBEB",
                    List.of("categories", "services", "service_variants", "provider_services")),
            new Domain(
                    "booking",
                    "Booking and money",
                    "#C2410C",
                    "#FFF7ED",
                    List.of(
                            "bookings",
                            "booking_items",
                            "booking_status_log",
                            "booking_status_transitions",
                            "coupons",
                            "coupon_redemptions",
                            "idempotency_keys",
                            "payments",
                            "refunds",
                            "cancellations")),
            new Domain(
                    "feedback",
                    "Feedback and provider operations",
                    "#7C3AED",
                    "#F5F3FF",
                    List.of(
                            "provider_reviews",
                            "service_reviews",
                            "complaints",
                            "provider_documents",
                            "provider_availability")));

    /** The core tables drawn in the README, and how each relationship reads (parent "label" child). */
    static final List<String> README_TABLES = List.of(
            "users",
            "customers",
            "service_providers",
            "customer_addresses",
            "locations",
            "bookings",
            "provider_services",
            "services",
            "service_variants",
            "booking_items",
            "payments",
            "provider_reviews");

    static final Map<String, String> README_LABELS = Map.ofEntries(
            Map.entry("customers>users", "is a"),
            Map.entry("service_providers>users", "is a"),
            Map.entry("customer_addresses>customers", "saves"),
            Map.entry("customer_addresses>locations", "saved as"),
            Map.entry("bookings>customers", "books"),
            Map.entry("bookings>locations", "hosts"),
            Map.entry("bookings>customer_addresses", "happens at"),
            Map.entry("bookings>service_providers", "fulfils"),
            Map.entry("provider_services>service_providers", "offers"),
            Map.entry("provider_services>services", "offered by"),
            Map.entry("service_variants>services", "has"),
            Map.entry("booking_items>bookings", "contains"),
            Map.entry("booking_items>services", "booked as"),
            Map.entry("booking_items>service_variants", "chosen as"),
            Map.entry("payments>bookings", "paid by"),
            Map.entry("provider_reviews>bookings", "reviewed in"));

    record Column(String name, String type, boolean notNull, String defaultExpr, boolean generated) {}

    record Key(String name, List<String> columns) {}

    record ForeignKey(
            String name,
            String table,
            List<String> columns,
            String refTable,
            List<String> refColumns,
            String onDelete) {}

    record Rule(String name, String definition) {}

    record Index(String name, boolean unique, String method, String columns, String where) {}

    record Trigger(String name, String timing, String events, String function) {}

    record Table(
            String name,
            List<Column> columns,
            Key primaryKey,
            List<Key> uniques,
            List<ForeignKey> foreignKeys,
            List<Rule> checks,
            List<Rule> exclusions,
            List<Index> indexes,
            List<Trigger> triggers) {

        Column column(String name) {
            return columns.stream()
                    .filter(c -> c.name().equals(name))
                    .findFirst()
                    .orElseThrow();
        }

        boolean isPrimaryKey(String column) {
            return primaryKey != null && primaryKey.columns().contains(column);
        }

        boolean isForeignKey(String column) {
            return foreignKeys.stream().anyMatch(fk -> fk.columns().contains(column));
        }

        boolean isUniqueAlone(String column) {
            return uniques.stream().anyMatch(u -> u.columns().equals(List.of(column)));
        }

        /**
         * True when no two rows can share these values: they include the primary key or a unique
         * constraint. For example {@code customers (user_id, role)} includes the unique {@code user_id}.
         */
        boolean isKey(List<String> cols) {
            if (primaryKey != null && cols.containsAll(primaryKey.columns())) {
                return true;
            }
            return uniques.stream().anyMatch(u -> cols.containsAll(u.columns()));
        }
    }

    record View(String name, boolean materialized, List<Index> indexes) {}

    record Routine(String name, String kind) {}

    record Schema(Map<String, Table> tables, List<View> views, List<Routine> routines) {}

    // ── Reading the catalog ────────────────────────────────────────────────────────────────

    static Schema read(JdbcTemplate jdbc) {
        Map<String, List<Column>> columns = new TreeMap<>();
        jdbc.query(
                """
                SELECT c.relname, a.attname, format_type(a.atttypid, a.atttypmod), a.attnotnull,
                       pg_get_expr(d.adbin, d.adrelid), a.attidentity <> ''
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
                WHERE n.nspname = ? AND c.relkind = 'r' AND c.relname <> 'flyway_schema_history'
                ORDER BY c.relname, a.attnum
                """,
                rs -> {
                    String def = rs.getString(5);
                    boolean serial = def != null && def.startsWith("nextval(");
                    columns.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                            .add(new Column(
                                    rs.getString(2),
                                    rs.getString(3),
                                    rs.getBoolean(4),
                                    serial ? null : def,
                                    serial || rs.getBoolean(6)));
                },
                SCHEMA);

        Map<String, Key> primaryKeys = new TreeMap<>();
        Map<String, List<Key>> uniques = new TreeMap<>();
        Map<String, List<ForeignKey>> foreignKeys = new TreeMap<>();
        Map<String, List<Rule>> checks = new TreeMap<>();
        Map<String, List<Rule>> exclusions = new TreeMap<>();
        jdbc.query(
                """
                SELECT rel.relname, con.conname, con.contype::text,
                       (SELECT string_agg(a.attname, ',' ORDER BY k.o)
                        FROM unnest(con.conkey) WITH ORDINALITY k(n, o)
                        JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k.n),
                       ref.relname,
                       (SELECT string_agg(a.attname, ',' ORDER BY k.o)
                        FROM unnest(con.confkey) WITH ORDINALITY k(n, o)
                        JOIN pg_attribute a ON a.attrelid = con.confrelid AND a.attnum = k.n),
                       con.confdeltype::text,
                       pg_get_constraintdef(con.oid)
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = rel.relnamespace
                LEFT JOIN pg_class ref ON ref.oid = con.confrelid
                WHERE n.nspname = ? AND rel.relkind = 'r' AND rel.relname <> 'flyway_schema_history'
                  AND con.contype IN ('p', 'u', 'f', 'c', 'x')
                ORDER BY rel.relname, con.conname
                """,
                rs -> {
                    String table = rs.getString(1);
                    String name = rs.getString(2);
                    List<String> cols = List.of(rs.getString(4).split(","));
                    switch (rs.getString(3)) {
                        case "p" -> primaryKeys.put(table, new Key(name, cols));
                        case "u" ->
                            uniques.computeIfAbsent(table, k -> new ArrayList<>())
                                    .add(new Key(name, cols));
                        case "f" ->
                            foreignKeys
                                    .computeIfAbsent(table, k -> new ArrayList<>())
                                    .add(new ForeignKey(
                                            name,
                                            table,
                                            cols,
                                            rs.getString(5),
                                            List.of(rs.getString(6).split(",")),
                                            onDelete(rs.getString(7))));
                        case "c" ->
                            checks.computeIfAbsent(table, k -> new ArrayList<>())
                                    .add(new Rule(name, pretty(rs.getString(8).replaceFirst("^CHECK ", ""))));
                        case "x" ->
                            exclusions
                                    .computeIfAbsent(table, k -> new ArrayList<>())
                                    .add(new Rule(name, exclusion(rs.getString(8))));
                        default -> throw new IllegalStateException();
                    }
                },
                SCHEMA);

        Map<String, List<Index>> indexes = new TreeMap<>();
        jdbc.query(
                """
                SELECT t.relname, i.relname, pg_get_indexdef(x.indexrelid)
                FROM pg_index x
                JOIN pg_class i ON i.oid = x.indexrelid
                JOIN pg_class t ON t.oid = x.indrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = ? AND t.relname <> 'flyway_schema_history'
                  AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid = x.indexrelid)
                ORDER BY t.relname, i.relname
                """,
                rs -> {
                    indexes.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                            .add(index(rs.getString(2), rs.getString(3)));
                },
                SCHEMA);

        Map<String, List<Trigger>> triggers = new TreeMap<>();
        jdbc.query(
                """
                SELECT event_object_table, trigger_name, action_timing,
                       string_agg(event_manipulation, ' or ' ORDER BY event_manipulation),
                       regexp_replace(action_statement, '^EXECUTE FUNCTION (sevak\\.)?', '')
                FROM information_schema.triggers
                WHERE trigger_schema = ?
                GROUP BY event_object_table, trigger_name, action_timing, action_statement
                ORDER BY event_object_table, trigger_name
                """,
                rs -> {
                    triggers.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                            .add(new Trigger(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
                },
                SCHEMA);

        Map<String, Table> tables = new LinkedHashMap<>();
        columns.forEach((name, cols) -> tables.put(
                name,
                new Table(
                        name,
                        cols,
                        primaryKeys.get(name),
                        uniques.getOrDefault(name, List.of()),
                        foreignKeys.getOrDefault(name, List.of()),
                        checks.getOrDefault(name, List.of()),
                        exclusions.getOrDefault(name, List.of()),
                        indexes.getOrDefault(name, List.of()),
                        triggers.getOrDefault(name, List.of()))));

        List<View> views = jdbc.query(
                """
                SELECT c.relname, c.relkind = 'm'
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind IN ('v', 'm')
                ORDER BY c.relname
                """,
                (rs, i) ->
                        new View(rs.getString(1), rs.getBoolean(2), indexes.getOrDefault(rs.getString(1), List.of())),
                SCHEMA);

        // Trigger functions are listed with their triggers. Functions that belong to an extension
        // (pg_trgm, btree_gist) or that PostgreSQL made for a range type are left out.
        List<Routine> routines = jdbc.query("""
                SELECT DISTINCT p.proname, CASE p.prokind WHEN 'p' THEN 'procedure' ELSE 'function' END
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = ? AND p.prorettype <> 'pg_catalog.trigger'::regtype
                  AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = p.oid AND d.deptype IN ('e', 'i'))
                ORDER BY 1
                """, (rs, i) -> new Routine(rs.getString(1), rs.getString(2)), SCHEMA);

        return new Schema(tables, views, routines);
    }

    private static String onDelete(String code) {
        return switch (code) {
            case "c" -> "cascade";
            case "n" -> "set null";
            case "d" -> "set default";
            case "r" -> "restrict";
            default -> null; // 'a' is NO ACTION, the default
        };
    }

    private static final Pattern INDEX_DEF =
            Pattern.compile("^CREATE (UNIQUE )?INDEX \\S+ ON \\S+ USING (\\w+) \\((.*)\\)$");

    private static Index index(String name, String definition) {
        String where = null;
        int at = definition.indexOf(" WHERE ");
        if (at >= 0) {
            where = pretty(definition.substring(at + " WHERE ".length()));
            definition = definition.substring(0, at);
        }
        Matcher m = INDEX_DEF.matcher(definition);
        if (!m.matches()) {
            throw new IllegalStateException("Unexpected index definition: " + definition);
        }
        return new Index(name, m.group(1) != null, m.group(2), pretty(m.group(3)), where);
    }

    private static String exclusion(String definition) {
        int at = definition.indexOf(" WHERE ");
        return at < 0
                ? pretty(definition)
                : pretty(definition.substring(0, at)) + " WHERE " + pretty(definition.substring(at + 7));
    }

    /** Makes PostgreSQL's fully parenthesised and cast-heavy expressions readable. */
    static String pretty(String sql) {
        String s = sql.replace(SCHEMA + ".", "")
                .replaceAll("::(character varying|text|numeric|integer|date)(\\[\\])?", "")
                .replaceAll("\\((\\w+)\\)", "$1")
                .replaceAll("= ANY \\(\\(?ARRAY\\[([^\\]]*)\\]\\)?\\)", "IN ($1)");
        while (s.startsWith("(") && s.endsWith(")") && closingParen(s, 0) == s.length() - 1) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static int closingParen(String s, int open) {
        int depth = 0;
        boolean quoted = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    static Domain domainOf(String table) {
        return DOMAINS.stream()
                .filter(d -> d.tables().contains(table))
                .findFirst()
                .orElse(null);
    }

    // ── Shared formatting ──────────────────────────────────────────────────────────────────

    /** Short, conventional type names: int, varchar(100), decimal(10,2), timestamptz. */
    static String shortType(String type) {
        return type.replace("character varying", "varchar")
                .replace("timestamp with time zone", "timestamptz")
                .replace("time without time zone", "time")
                .replace("numeric", "decimal")
                .replace("integer", "int");
    }

    private static String defaultValue(String expr) {
        Matcher literal = Pattern.compile("^'(.*)'::[\\w ]+$").matcher(expr);
        if (literal.matches()) {
            return "'" + literal.group(1) + "'";
        }
        if (expr.matches("-?\\d+(\\.\\d+)?|true|false")) {
            return expr;
        }
        return "`" + expr + "`";
    }

    private static String list(List<String> columns) {
        return String.join(", ", columns);
    }

    static String header(String commentPrefix) {
        return commentPrefix
                + " Generated from the database that the Flyway migrations build. Don't edit by hand.\n"
                + commentPrefix
                + " Regenerate: see docs/schema/README.md\n";
    }

    // ── DBML (dbdiagram.io) ───────────────────────────────────────────────────────────────

    static String dbml(Schema schema) {
        StringBuilder out = new StringBuilder(header("//"));
        out.append("// Paste into https://dbdiagram.io to explore it.\n\n");
        for (Domain d : DOMAINS) {
            out.append("TableGroup ")
                    .append(d.key())
                    .append(" [color: ")
                    .append(d.colour())
                    .append("] {\n");
            d.tables().forEach(t -> out.append("  ").append(t).append('\n'));
            out.append("}\n");
        }
        for (Domain d : DOMAINS) {
            for (String name : d.tables()) {
                Table t = schema.tables().get(name);
                out.append("\nTable ")
                        .append(name)
                        .append(" [headercolor: ")
                        .append(d.colour())
                        .append("] {\n");
                boolean singlePk =
                        t.primaryKey() != null && t.primaryKey().columns().size() == 1;
                for (Column c : t.columns()) {
                    List<String> settings = new ArrayList<>();
                    if (singlePk && t.isPrimaryKey(c.name())) {
                        settings.add("pk");
                    }
                    if (c.generated()) {
                        settings.add("increment");
                    }
                    if (c.notNull() && !(singlePk && t.isPrimaryKey(c.name()))) {
                        settings.add("not null");
                    }
                    if (t.isUniqueAlone(c.name())) {
                        settings.add("unique");
                    }
                    if (c.defaultExpr() != null) {
                        settings.add("default: " + defaultValue(c.defaultExpr()));
                    }
                    out.append("  ").append(c.name()).append(' ').append(shortType(c.type()));
                    if (!settings.isEmpty()) {
                        out.append(" [").append(String.join(", ", settings)).append(']');
                    }
                    out.append('\n');
                }
                List<String> indexLines = new ArrayList<>();
                if (t.primaryKey() != null && !singlePk) {
                    indexLines.add("(" + list(t.primaryKey().columns()) + ") [pk]");
                }
                for (Key u : t.uniques()) {
                    if (u.columns().size() > 1) {
                        indexLines.add("(" + list(u.columns()) + ") [unique, name: '" + u.name() + "']");
                    }
                }
                for (Index i : t.indexes()) {
                    List<String> settings = new ArrayList<>();
                    if (i.unique()) {
                        settings.add("unique");
                    }
                    settings.add("name: '" + i.name() + "'");
                    String note = i.method().equals("btree") ? "" : "using " + i.method();
                    if (i.where() != null) {
                        note = (note.isEmpty() ? "" : note + ", ") + "where " + i.where();
                    }
                    if (!note.isEmpty()) {
                        settings.add("note: '" + note.replace("'", "\\'") + "'");
                    }
                    indexLines.add("(" + dbmlIndexColumns(i.columns()) + ") [" + String.join(", ", settings) + "]");
                }
                if (!indexLines.isEmpty()) {
                    out.append("  indexes {\n");
                    indexLines.forEach(l -> out.append("    ").append(l).append('\n'));
                    out.append("  }\n");
                }
                List<String> notes = new ArrayList<>();
                t.checks().forEach(c -> notes.add("CHECK " + c.name() + ": " + c.definition()));
                t.exclusions().forEach(x -> notes.add(x.name() + ": " + x.definition()));
                t.triggers()
                        .forEach(tr -> notes.add("TRIGGER " + tr.name() + ": " + tr.timing() + " " + tr.events()
                                + " -> " + tr.function()));
                if (!notes.isEmpty()) {
                    out.append("  Note: '''\n");
                    notes.forEach(n -> out.append("  ").append(n).append('\n'));
                    out.append("  '''\n");
                }
                out.append("}\n");
            }
        }
        out.append('\n');
        for (Table t : orderedTables(schema)) {
            for (ForeignKey fk : t.foreignKeys()) {
                out.append("Ref ")
                        .append(fk.name())
                        .append(": ")
                        .append(dbmlColumns(t.name(), fk.columns()))
                        .append(" > ")
                        .append(dbmlColumns(fk.refTable(), fk.refColumns()));
                if (fk.onDelete() != null) {
                    out.append(" [delete: ").append(fk.onDelete()).append(']');
                }
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String dbmlColumns(String table, List<String> cols) {
        return cols.size() == 1 ? table + "." + cols.get(0) : table + ".(" + list(cols) + ")";
    }

    /** Plain column names stay as they are; expressions and sort orders go in backticks. */
    private static String dbmlIndexColumns(String columns) {
        return splitTopLevel(columns).stream()
                .map(c -> c.matches("\\w+") ? c : "`" + c + "`")
                .collect(Collectors.joining(", "));
    }

    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(s.substring(start).trim());
        return parts;
    }

    static List<Table> orderedTables(Schema schema) {
        return DOMAINS.stream()
                .flatMap(d -> d.tables().stream())
                .map(schema.tables()::get)
                .toList();
    }

    // ── Graphviz (the picture) ────────────────────────────────────────────────────────────

    /** The graph id carries a hash of the rest of the file, so a stale SVG can be detected. */
    static String dot(Schema schema) {
        StringBuilder body = new StringBuilder();
        body.append("  graph [rankdir=LR, fontname=\"Helvetica\", fontsize=22, labelloc=t, nodesep=0.25,")
                .append(" ranksep=1.1, pad=0.4, bgcolor=\"white\",\n")
                .append("         label=<<b>SEVAK physical schema</b><br/><font point-size=\"13\">")
                .append(schema.tables().size())
                .append(" tables, generated from the Flyway-built database. PK primary key, FK foreign key,")
                .append(" UK unique, * NOT NULL</font>>]\n")
                .append("  node [shape=plain, fontname=\"Helvetica\", fontsize=11]\n")
                .append("  edge [color=\"#64748B\", penwidth=1.1, dir=both]\n");
        for (Domain d : DOMAINS) {
            body.append("\n  subgraph cluster_")
                    .append(d.key())
                    .append(" {\n")
                    .append("    label=<<b>")
                    .append(d.title())
                    .append("</b>>; fontsize=16; fontcolor=\"")
                    .append(d.colour())
                    .append("\"; style=\"rounded,filled\"; color=\"")
                    .append(d.colour())
                    .append("\"; fillcolor=\"")
                    .append(d.fill())
                    .append("\"\n");
            for (String name : d.tables()) {
                body.append("    ")
                        .append(name)
                        .append(" [label=<")
                        .append(dotTable(schema.tables().get(name), d))
                        .append(">]\n");
            }
            body.append("  }\n");
        }
        body.append('\n');
        for (Table t : orderedTables(schema)) {
            Set<String> drawn = new LinkedHashSet<>();
            for (ForeignKey fk : t.foreignKeys()) {
                // One line per pair of tables: a composite key and its single-column twin are one link.
                if (!drawn.add(fk.refTable())) {
                    continue;
                }
                boolean optional =
                        fk.columns().stream().anyMatch(c -> !t.column(c).notNull());
                body.append("  ")
                        .append(t.name())
                        .append(':')
                        .append(fk.columns().get(0))
                        .append(" -> ")
                        .append(fk.refTable())
                        .append(':')
                        .append(fk.refColumns().get(0))
                        .append(" [arrowtail=")
                        .append(t.isKey(fk.columns()) ? "teeodot" : "crowodot")
                        .append(", arrowhead=")
                        .append(optional ? "teeodot" : "teetee")
                        .append("]\n");
            }
        }
        String text = body.toString();
        return "digraph sevak {\n  id=\"schema_" + sha256(text).substring(0, 16) + "\"\n" + text + "}\n";
    }

    private static String dotTable(Table t, Domain d) {
        StringBuilder html = new StringBuilder(
                "<table border=\"0\" cellborder=\"1\" cellspacing=\"0\" cellpadding=\"3\" bgcolor=\"white\">");
        html.append("<tr><td colspan=\"3\" bgcolor=\"")
                .append(d.colour())
                .append("\"><font color=\"white\"><b>")
                .append(t.name())
                .append("</b></font></td></tr>");
        for (Column c : t.columns()) {
            List<String> keys = new ArrayList<>();
            if (t.isPrimaryKey(c.name())) {
                keys.add("PK");
            }
            if (t.isForeignKey(c.name())) {
                keys.add("FK");
            }
            if (t.isUniqueAlone(c.name())) {
                keys.add("UK");
            }
            html.append("<tr><td port=\"")
                    .append(c.name())
                    .append("\" align=\"left\">")
                    .append(t.isPrimaryKey(c.name()) ? "<b>" + c.name() + "</b>" : c.name())
                    .append(c.notNull() ? " *" : "")
                    .append("</td><td align=\"left\"><font color=\"#475569\">")
                    .append(escape(shortType(c.type())))
                    .append("</font></td><td>")
                    .append(
                            keys.isEmpty()
                                    ? " "
                                    : "<font color=\"" + d.colour() + "\">" + String.join(" ", keys) + "</font>")
                    .append("</td></tr>");
        }
        List<String> rules = new ArrayList<>();
        t.uniques().stream()
                .filter(u -> u.columns().size() > 1)
                .forEach(u -> rules.add("UNIQUE (" + list(u.columns()) + ")"));
        t.exclusions().forEach(x -> rules.add(x.name()));
        t.indexes().stream()
                .filter(i -> i.unique() || i.where() != null || !i.method().equals("btree"))
                .forEach(i -> rules.add(i.name()));
        if (!t.checks().isEmpty()) {
            rules.add(t.checks().size() + (t.checks().size() == 1 ? " CHECK" : " CHECKs"));
        }
        if (!t.triggers().isEmpty()) {
            rules.add(t.triggers().size() + (t.triggers().size() == 1 ? " trigger" : " triggers"));
        }
        if (!rules.isEmpty()) {
            html.append("<tr><td colspan=\"3\" align=\"left\" bgcolor=\"#F8FAFC\"><font point-size=\"9\""
                            + " color=\"#475569\">")
                    .append(escape(String.join(" · ", rules)))
                    .append("</font></td></tr>");
        }
        return html.append("</table>").toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String sha256(String s) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── Course-style relational schema (text) ─────────────────────────────────────────────

    static String relational(Schema schema) {
        StringBuilder out = new StringBuilder(header("<!--").replace("\n", " -->\n"));
        out.append("\n# SEVAK: relational schema\n\n")
                .append(schema.tables().size())
                .append(" tables in the `sevak` schema. Notation: <ins>primary key</ins> is underlined,")
                .append(" *foreign key* is in italics, and a single-column foreign key is followed by")
                .append(" `→` and the table it references. Every constraint below is copied from the")
                .append(" database itself.\n");
        for (Domain d : DOMAINS) {
            out.append("\n## ").append(d.title()).append('\n');
            for (String name : d.tables()) {
                Table t = schema.tables().get(name);
                List<String> cols = new ArrayList<>();
                for (Column c : t.columns()) {
                    String col = t.isForeignKey(c.name()) ? "*" + c.name() + "*" : c.name();
                    if (t.isPrimaryKey(c.name())) {
                        col = "<ins>" + col + "</ins>";
                    }
                    String target = t.foreignKeys().stream()
                            .filter(fk -> fk.columns().equals(List.of(c.name())))
                            .map(ForeignKey::refTable)
                            .findFirst()
                            .orElse(null);
                    cols.add(target == null ? col : col + " → " + target);
                }
                out.append("\n**")
                        .append(name)
                        .append("**(")
                        .append(String.join(", ", cols))
                        .append(")\n\n");
                List<String> facts = new ArrayList<>();
                t.foreignKeys().stream()
                        .filter(fk -> fk.columns().size() > 1)
                        .forEach(fk -> facts.add("Foreign key (" + list(fk.columns()) + ") → " + fk.refTable() + "("
                                + list(fk.refColumns()) + ")"));
                List<String> deletes = t.foreignKeys().stream()
                        .filter(fk -> fk.onDelete() != null)
                        .map(fk -> (fk.columns().size() == 1 ? fk.columns().get(0) : "(" + list(fk.columns()) + ")")
                                + " " + fk.onDelete())
                        .toList();
                if (!deletes.isEmpty()) {
                    facts.add("On delete: " + String.join("; ", deletes));
                }
                t.uniques().forEach(u -> facts.add("Unique (" + list(u.columns()) + ")"));
                t.checks().forEach(c -> facts.add("Check: `" + c.definition() + "`"));
                t.exclusions().forEach(x -> facts.add("Exclusion `" + x.name() + "`: `" + x.definition() + "`"));
                t.indexes()
                        .forEach(i -> facts.add((i.unique() ? "Unique index `" : "Index `") + i.name() + "`: "
                                + (i.method().equals("btree") ? "" : i.method() + " ") + "(" + i.columns() + ")"
                                + (i.where() == null ? "" : " where `" + i.where() + "`")));
                t.triggers()
                        .forEach(tr -> facts.add(
                                "Trigger `" + tr.name() + "`: " + tr.timing().toLowerCase() + " "
                                        + tr.events().toLowerCase() + " → `" + tr.function() + "`"));
                facts.forEach(f -> out.append("- ").append(f).append('\n'));
            }
        }
        out.append("\n## Views and routines\n\n");
        for (View v : schema.views()) {
            out.append("- ")
                    .append(v.materialized() ? "Materialized view" : "View")
                    .append(" `")
                    .append(v.name())
                    .append('`');
            v.indexes()
                    .forEach(i -> out.append(", ")
                            .append(i.unique() ? "unique index on (" : "index on (")
                            .append(i.columns())
                            .append(')'));
            out.append('\n');
        }
        for (Routine r : schema.routines()) {
            out.append("- ")
                    .append(r.kind().equals("procedure") ? "Procedure" : "Function")
                    .append(" `")
                    .append(r.name())
                    .append("`\n");
        }
        return out.toString();
    }

    // ── Mermaid (the README) ──────────────────────────────────────────────────────────────

    static String mermaid(Schema schema) {
        StringBuilder out = new StringBuilder("```mermaid\nerDiagram\n");
        for (String name : README_TABLES) {
            Table t = schema.tables().get(name);
            Set<String> drawn = new LinkedHashSet<>();
            for (ForeignKey fk : t.foreignKeys()) {
                if (!README_TABLES.contains(fk.refTable()) || !drawn.add(fk.refTable())) {
                    continue;
                }
                boolean optional =
                        fk.columns().stream().anyMatch(c -> !t.column(c).notNull());
                String label = README_LABELS.getOrDefault(name + ">" + fk.refTable(), list(fk.columns()));
                out.append("    ")
                        .append(fk.refTable().toUpperCase())
                        .append(optional ? " |o--" : " ||--")
                        .append(t.isKey(fk.columns()) ? "o| " : "o{ ")
                        .append(name.toUpperCase())
                        .append(" : \"")
                        .append(label)
                        .append("\"\n");
            }
        }
        for (String name : README_TABLES) {
            Table t = schema.tables().get(name);
            out.append("    ").append(name.toUpperCase()).append(" {\n");
            for (Column c : t.columns()) {
                List<String> keys = new ArrayList<>();
                if (t.isPrimaryKey(c.name())) {
                    keys.add("PK");
                }
                if (t.isForeignKey(c.name())) {
                    keys.add("FK");
                }
                if (t.isUniqueAlone(c.name())) {
                    keys.add("UK");
                }
                if (keys.isEmpty()) {
                    continue; // key columns only; the full diagram has the rest
                }
                out.append("        ")
                        .append(shortType(c.type()).replaceAll("\\(.*\\)", ""))
                        .append(' ')
                        .append(c.name())
                        .append(' ')
                        .append(String.join(", ", keys))
                        .append('\n');
            }
            out.append("    }\n");
        }
        return out.append("```\n").toString();
    }
}
