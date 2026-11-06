package com.rag.common.core.architecture;

import com.rag.common.core.testfixture.TestRepoPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards ADR-0006: every runnable RAG implementation writes to its own
 * PostgreSQL schema and keeps the shared {@code chunks} table name. The schema
 * must come from a module-specific variable, because one shared variable makes
 * the last-started module write into the other module's schema.
 */
class ModuleSchemaIsolationTest {

    private static final String APPLICATION_YML = "src/main/resources/application.yml";
    private static final String ENV_FILE = ".env";
    private static final String SCHEMA_KEY = "schema-name:";
    private static final String TABLE_KEY = "table-name:";
    private static final String TABLE_NAME = "chunks";
    private static final String LEGACY_SHARED_VARIABLE = "${PGVECTOR_SCHEMA";
    private static final String LEGACY_SHARED_KEY = "PGVECTOR_SCHEMA";

    private static final Map<String, String> MODULE_SCHEMAS = Map.of(
            "rag-basic", "rag_basic",
            "rag-advanced", "rag_advanced",
            "rag-agentic", "rag_agentic");

    private static final Map<String, String> MODULE_VARIABLES = Map.of(
            "rag-basic", "PGVECTOR_BASIC_SCHEMA",
            "rag-advanced", "PGVECTOR_ADVANCED_SCHEMA",
            "rag-agentic", "PGVECTOR_AGENTIC_SCHEMA");

    static Stream<Arguments> modules() {
        return MODULE_SCHEMAS.keySet().stream().sorted()
                .map(module -> Arguments.of(module, MODULE_VARIABLES.get(module), MODULE_SCHEMAS.get(module)));
    }

    @ParameterizedTest(name = "{0} pins schema {2}")
    @MethodSource("modules")
    void shouldPinOwnSchemaAndChunksTableWhenModuleUsesPgVector(String module, String variable, String schema)
            throws IOException {
        var config = moduleConfig(module);
        assertThat(property(config, SCHEMA_KEY))
                .as("%s must resolve its own schema, never another module's", module)
                .isEqualTo("${%s:%s}".formatted(variable, schema));
        assertThat(property(config, TABLE_KEY))
                .as("%s keeps the ADR-0006 table name", module)
                .isEqualTo(TABLE_NAME);
        assertThat(config)
                .as("%s must not read a schema variable shared with another module", module)
                .doesNotContain(LEGACY_SHARED_VARIABLE);
    }

    @Test
    void shouldDefineOneDistinctSchemaPerModuleInEnv() throws IOException {
        var env = envValues();
        assertThat(MODULE_SCHEMAS).allSatisfy((module, schema) ->
                assertThat(env).containsEntry(MODULE_VARIABLES.get(module), schema));
        assertThat(MODULE_SCHEMAS.values()).as("every module needs a distinct schema").doesNotHaveDuplicates();
        assertThat(env).doesNotContainKey(LEGACY_SHARED_KEY);
    }

    private static String moduleConfig(String module) throws IOException {
        return Files.readString(TestRepoPaths.root().resolve("apps").resolve(module).resolve(APPLICATION_YML));
    }

    private static String property(String config, String key) {
        return config.lines()
                .map(String::trim)
                .filter(line -> line.startsWith(key))
                .map(line -> line.substring(key.length()).trim())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + key + " in configuration"));
    }

    private static Map<String, String> envValues() throws IOException {
        var values = new HashMap<String, String>();
        for (var line : Files.readAllLines(TestRepoPaths.root().resolve(ENV_FILE))) {
            var separator = line.indexOf('=');
            if (separator > 0 && !line.stripLeading().startsWith("#")) {
                values.put(line.substring(0, separator).strip(), line.substring(separator + 1).strip());
            }
        }
        return values;
    }
}
