package com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.git;

import com.ceticgroup.cloud.nodeprovider.logtriage.config.GitWorkspaceProperties;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.CodeSnippet;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Incident;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.LogSnippet;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.CodeContextPort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Construit le contexte de code à partir du checkout local. Stratégie minimale : extraire les
 * références "Class.java:42" depuis les stack traces des logs, et joindre un extrait autour de
 * chaque ligne. Une refresh git pull est tentée best-effort avant l'extraction.
 */
public final class GitGraphContextAdapter implements CodeContextPort {

    private static final Logger LOG = LoggerFactory.getLogger(GitGraphContextAdapter.class);
    private static final Pattern STACK_FRAME = Pattern.compile("(\\w[\\w.$]+)\\.java:(\\d+)");
    private static final int CONTEXT_LINES = 20;

    private final GitWorkspaceProperties properties;

    public GitGraphContextAdapter(GitWorkspaceProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public List<CodeSnippet> gatherFor(Incident incident, List<LogSnippet> logs) {
        Path repoRoot = properties.repoRoot();
        if (!Files.isDirectory(repoRoot)) {
            LOG.warn("git workspace not initialized at {}", repoRoot);
            return List.of();
        }

        tryPull(repoRoot);

        Set<String> seen = new HashSet<>();
        List<CodeSnippet> out = new ArrayList<>();
        for (LogSnippet log : logs) {
            Matcher m = STACK_FRAME.matcher(log.message());
            while (m.find()) {
                String simpleName = m.group(1);
                int line = Integer.parseInt(m.group(2));
                String key = simpleName + ":" + line;
                if (!seen.add(key)) {
                    continue;
                }
                findSource(repoRoot, simpleName)
                        .flatMap(p -> readSnippet(repoRoot, p, line))
                        .ifPresent(out::add);
                if (out.size() >= properties.maxSnippets()) {
                    return out;
                }
            }
        }
        return out;
    }

    private void tryPull(Path repoRoot) {
        try (Git git = Git.open(repoRoot.toFile())) {
            git.fetch().call();
        } catch (IOException | GitAPIException e) {
            LOG.debug("git fetch best-effort failed: {}", e.toString());
        }
    }

    private java.util.Optional<Path> findSource(Path repoRoot, String simpleName) {
        String fileName = lastSegment(simpleName) + ".java";
        try (var stream = Files.walk(repoRoot)) {
            return stream.filter(p -> p.getFileName() != null)
                    .filter(p -> fileName.equals(p.getFileName().toString()))
                    .findFirst();
        } catch (IOException e) {
            return java.util.Optional.empty();
        }
    }

    private static String lastSegment(String dotted) {
        int idx = dotted.lastIndexOf('.');
        return idx < 0 ? dotted : dotted.substring(idx + 1);
    }

    private java.util.Optional<CodeSnippet> readSnippet(Path repoRoot, Path file, int line) {
        try {
            List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
            int start = Math.max(1, line - CONTEXT_LINES);
            int end = Math.min(all.size(), line + CONTEXT_LINES);
            String content = String.join("\n", all.subList(start - 1, end));
            String rel = repoRoot.relativize(file).toString().replace('\\', '/');
            return java.util.Optional.of(new CodeSnippet(rel, start, end, content));
        } catch (IOException e) {
            return java.util.Optional.empty();
        }
    }
}
