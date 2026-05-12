package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FsElDatadirTemplateLocatorTest {

    @Test
    void findTemplate_should_returnEmpty_when_templateMissing(@TempDir Path templatesDir) {
        FsElDatadirTemplateLocator locator = new FsElDatadirTemplateLocator(templatesDir);

        Optional<Path> result = locator.findTemplate(Network.HOODI, ElClient.BESU);

        assertThat(result).isEmpty();
    }

    @Test
    void findTemplate_should_returnPath_when_templateExistsForCouple(@TempDir Path templatesDir)
            throws IOException {
        Path tarball = templatesDir.resolve("hoodi-besu.tar.zst");
        Files.writeString(tarball, "fake");

        FsElDatadirTemplateLocator locator = new FsElDatadirTemplateLocator(templatesDir);

        Optional<Path> result = locator.findTemplate(Network.HOODI, ElClient.BESU);

        assertThat(result).hasValue(tarball);
    }

    @Test
    void findTemplate_should_namingConventionLowercase_forNetworkAndEl(@TempDir Path templatesDir)
            throws IOException {
        Path tarball = templatesDir.resolve("sepolia-geth.tar.zst");
        Files.writeString(tarball, "fake");

        FsElDatadirTemplateLocator locator = new FsElDatadirTemplateLocator(templatesDir);

        Optional<Path> result = locator.findTemplate(Network.SEPOLIA, ElClient.GETH);

        assertThat(result).hasValue(tarball);
    }

    @Test
    void findTemplate_should_returnEmpty_when_pathExistsButIsDirectory(@TempDir Path templatesDir)
            throws IOException {
        // Defensive: a stray directory matching the template name must not be treated as a tarball.
        Files.createDirectory(templatesDir.resolve("hoodi-besu.tar.zst"));

        FsElDatadirTemplateLocator locator = new FsElDatadirTemplateLocator(templatesDir);

        Optional<Path> result = locator.findTemplate(Network.HOODI, ElClient.BESU);

        assertThat(result).isEmpty();
    }

    @Test
    void findTemplate_should_returnEmpty_when_templatesDirItselfMissing(@TempDir Path parent)
            throws IOException {
        Path nonexistent = parent.resolve("does-not-exist");
        FsElDatadirTemplateLocator locator = new FsElDatadirTemplateLocator(nonexistent);

        Optional<Path> result = locator.findTemplate(Network.HOODI, ElClient.BESU);

        assertThat(result).isEmpty();
    }
}
