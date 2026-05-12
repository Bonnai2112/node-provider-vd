package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    @Test
    void extractTarballZstd_should_restoreFilesIntoTargetDir(@TempDir Path tmp) throws IOException {
        assumeTrue(commandAvailable("tar") && commandAvailable("zstd"));

        Path source = Files.createDirectory(tmp.resolve("source"));
        Files.writeString(source.resolve("chaindata.txt"), "hello-from-template");
        Files.createDirectory(source.resolve("subdir"));
        Files.writeString(source.resolve("subdir/nested.txt"), "nested");

        Path tarball = tmp.resolve("template.tar.zst");
        runShell(tmp, "tar --use-compress-program=zstd -cf " + tarball + " -C " + source + " .");

        Path target = tmp.resolve("target");
        runner.extractTarballZstd(tarball, target);

        assertThat(target.resolve("chaindata.txt")).exists().hasContent("hello-from-template");
        assertThat(target.resolve("subdir/nested.txt")).exists().hasContent("nested");
    }

    @Test
    void extractTarballZstd_should_createTargetDir_when_missing(@TempDir Path tmp)
            throws IOException {
        assumeTrue(commandAvailable("tar") && commandAvailable("zstd"));

        Path source = Files.createDirectory(tmp.resolve("source"));
        Files.writeString(source.resolve("x.txt"), "x");
        Path tarball = tmp.resolve("t.tar.zst");
        runShell(tmp, "tar --use-compress-program=zstd -cf " + tarball + " -C " + source + " .");

        Path target = tmp.resolve("not/yet/there");
        runner.extractTarballZstd(tarball, target);

        assertThat(target.resolve("x.txt")).exists().hasContent("x");
    }

    private static boolean commandAvailable(String command) {
        try {
            Process p = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void runShell(Path workdir, String script) throws IOException {
        try {
            Process p =
                    new ProcessBuilder("bash", "-lc", script)
                            .directory(workdir.toFile())
                            .redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .start();
            if (p.waitFor() != 0) {
                throw new IOException("test fixture shell failed: " + script);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private static void writeDefaultEnv(Path tmp, String content) throws IOException {
        Files.writeString(tmp.resolve("default.env"), content, StandardCharsets.UTF_8);
    }
}
