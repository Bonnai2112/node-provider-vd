package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessEthdShellRunnerTest {

    private final ProcessEthdShellRunner runner = new ProcessEthdShellRunner();

    @Test
    void readDefaultEnv_should_parseSimpleKeyValuePairs(@TempDir Path tmp) throws IOException {
        writeDefaultEnv(
                tmp,
                """
                ENV_VERSION=55
                LOG_LEVEL=info
                NETWORK=hoodi
                """);

        Map<String, String> env = runner.readDefaultEnv(tmp);

        assertThat(env)
                .containsEntry("ENV_VERSION", "55")
                .containsEntry("LOG_LEVEL", "info")
                .containsEntry("NETWORK", "hoodi");
    }

    @Test
    void readDefaultEnv_should_skipCommentsAndBlanks(@TempDir Path tmp) throws IOException {
        writeDefaultEnv(
                tmp,
                """
                # this is a comment
                ENV_VERSION=55

                # another comment
                LOG_LEVEL=info
                """);

        Map<String, String> env = runner.readDefaultEnv(tmp);

        assertThat(env).hasSize(2).containsEntry("ENV_VERSION", "55");
    }

    @Test
    void readDefaultEnv_should_stitchMultilineQuotedValue(@TempDir Path tmp) throws IOException {
        writeDefaultEnv(
                tmp,
                """
                MEV_RELAYS="
                https://relay-a.example,
                https://relay-b.example
                "
                NEXT_KEY=after
                """);

        Map<String, String> env = runner.readDefaultEnv(tmp);

        assertThat(env.get("MEV_RELAYS"))
                .startsWith("\"")
                .endsWith("\"")
                .contains("relay-a.example")
                .contains("relay-b.example");
        assertThat(env).containsEntry("NEXT_KEY", "after");
    }

    @Test
    void readDefaultEnv_should_keepSingleQuotedShellSubstitutionAsIs(@TempDir Path tmp)
            throws IOException {
        writeDefaultEnv(
                tmp,
                """
                GETH_SRC_BUILD_TARGET='$(git describe --tags)'
                ENV_VERSION=55
                """);

        Map<String, String> env = runner.readDefaultEnv(tmp);

        assertThat(env).containsEntry("GETH_SRC_BUILD_TARGET", "'$(git describe --tags)'");
    }

    @Test
    void readDefaultEnv_should_keepEmptyValue(@TempDir Path tmp) throws IOException {
        writeDefaultEnv(
                tmp,
                """
                MEV_MIN_BID=
                ENV_VERSION=55
                """);

        Map<String, String> env = runner.readDefaultEnv(tmp);

        assertThat(env).containsEntry("MEV_MIN_BID", "");
    }

    private static void writeDefaultEnv(Path tmp, String content) throws IOException {
        Files.writeString(tmp.resolve("default.env"), content, StandardCharsets.UTF_8);
    }
}
