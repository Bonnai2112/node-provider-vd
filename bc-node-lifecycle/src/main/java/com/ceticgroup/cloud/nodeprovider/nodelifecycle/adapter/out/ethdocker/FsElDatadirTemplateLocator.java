package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ElDatadirTemplateLocator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public class FsElDatadirTemplateLocator implements ElDatadirTemplateLocator {

    private final Path templatesDir;

    public FsElDatadirTemplateLocator(Path templatesDir) {
        this.templatesDir = templatesDir;
    }

    @Override
    public Optional<Path> findTemplate(Network network, ElClient el) {
        Path candidate = templatesDir.resolve(templateFileName(network, el));
        return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    private static String templateFileName(Network network, ElClient el) {
        return network.name().toLowerCase(Locale.ROOT)
                + "-"
                + el.name().toLowerCase(Locale.ROOT)
                + ".tar.zst";
    }
}
