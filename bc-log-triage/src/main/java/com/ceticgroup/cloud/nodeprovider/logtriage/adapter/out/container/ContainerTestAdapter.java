package com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.container;

import com.ceticgroup.cloud.nodeprovider.logtriage.config.ContainerTestProperties;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.FilePatch;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.ProposedFix;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.VerificationReport;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FixVerificationPort;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vérifie un correctif en clonant le repo dans un répertoire jetable, appliquant les patchs, puis
 * lançant {@code mvn -B -q verify} dans un conteneur Maven. Le conteneur est détruit après
 * exécution. Toute erreur d'exécution (image manquante, IO) résulte en un rapport négatif — jamais
 * en une exception qui remonterait au domaine.
 */
public final class ContainerTestAdapter implements FixVerificationPort {

    private static final Logger LOG = LoggerFactory.getLogger(ContainerTestAdapter.class);

    private final DockerClient docker;
    private final ContainerTestProperties properties;

    public ContainerTestAdapter(DockerClient docker, ContainerTestProperties properties) {
        this.docker = Objects.requireNonNull(docker, "docker");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public VerificationReport verify(ProposedFix fix) {
        Path workdir;
        try {
            workdir = prepareWorkdir(fix);
        } catch (IOException | GitAPIException e) {
            return new VerificationReport(false, false, "prepare failed: " + e);
        }

        try {
            return runMavenVerify(workdir);
        } finally {
            deleteRecursively(workdir);
        }
    }

    private Path prepareWorkdir(ProposedFix fix) throws IOException, GitAPIException {
        Path workdir = Files.createTempDirectory("log-triage-verify-");
        Git.cloneRepository()
                .setURI(properties.repoUrl())
                .setDirectory(workdir.toFile())
                .setBranch(properties.baseBranch())
                .setDepth(1)
                .call()
                .close();
        for (FilePatch patch : fix.patches()) {
            Path target = workdir.resolve(patch.path()).normalize();
            if (!target.startsWith(workdir)) {
                throw new IOException("patch escapes workdir: " + patch.path());
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, patch.newContent(), StandardCharsets.UTF_8);
        }
        return workdir;
    }

    private VerificationReport runMavenVerify(Path workdir) {
        HostConfig hostConfig =
                HostConfig.newHostConfig()
                        .withBinds(new Bind(workdir.toString(), new Volume("/workdir")))
                        .withAutoRemove(true);

        CreateContainerResponse created = null;
        try {
            created =
                    docker.createContainerCmd(properties.mavenImage())
                            .withWorkingDir("/workdir")
                            .withHostConfig(hostConfig)
                            .withCmd("mvn", "-B", "-q", "verify")
                            .exec();
            docker.startContainerCmd(created.getId()).exec();

            StringBuilder output = new StringBuilder(8192);
            docker.logContainerCmd(created.getId())
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(
                            new LogContainerResultCallback() {
                                @Override
                                public void onNext(com.github.dockerjava.api.model.Frame frame) {
                                    output.append(
                                            new String(frame.getPayload(), StandardCharsets.UTF_8));
                                }
                            })
                    .awaitCompletion();

            int exit =
                    docker.waitContainerCmd(created.getId())
                            .exec(new WaitContainerResultCallback())
                            .awaitStatusCode(
                                    properties.timeoutSeconds(),
                                    java.util.concurrent.TimeUnit.SECONDS);
            boolean ok = exit == 0;
            return new VerificationReport(ok, ok, output.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new VerificationReport(false, false, "interrupted: " + e);
        } catch (RuntimeException e) {
            LOG.warn("verification container run failed: {}", e.toString());
            return new VerificationReport(false, false, "container error: " + e);
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(
                            p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
        } catch (IOException ignored) {
        }
    }

    @SuppressWarnings("unused")
    private static String randomToken() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
