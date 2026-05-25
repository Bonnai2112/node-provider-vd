package com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.gitlab;

import com.ceticgroup.cloud.nodeprovider.logtriage.config.GitLabProperties;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.FilePatch;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Incident;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.MergeRequestRef;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.ProposedFix;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.ValidatedFix;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.MergeRequestPort;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.CommitAction;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestParams;
import org.gitlab4j.api.models.RepositoryFile;

public final class GitLabMergeRequestAdapter implements MergeRequestPort {

    private static final String DRAFT_PREFIX = "Draft: ";

    private final GitLabApi gitLab;
    private final GitLabProperties properties;

    public GitLabMergeRequestAdapter(GitLabApi gitLab, GitLabProperties properties) {
        this.gitLab = Objects.requireNonNull(gitLab, "gitLab");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public MergeRequestRef openDraft(ValidatedFix validated, Incident incident) {
        ProposedFix fix = validated.fix();
        String branch = uniqueBranchName(fix.suggestedBranchName(), incident);
        try {
            gitLab.getRepositoryApi()
                    .createBranch(properties.projectPath(), branch, properties.baseBranch());

            List<CommitAction> actions = buildCommitActions(fix);
            gitLab.getCommitsApi()
                    .createCommit(
                            properties.projectPath(),
                            branch,
                            fix.suggestedCommitMessage(),
                            null,
                            null,
                            null,
                            actions);

            MergeRequestParams params =
                    new MergeRequestParams()
                            .withSourceBranch(branch)
                            .withTargetBranch(properties.baseBranch())
                            .withTitle(DRAFT_PREFIX + safeTitle(incident, fix))
                            .withDescription(renderDescription(validated, incident));

            MergeRequest mr =
                    gitLab.getMergeRequestApi()
                            .createMergeRequest(properties.projectPath(), params);
            return new MergeRequestRef(
                    properties.projectPath(), mr.getIid(), URI.create(mr.getWebUrl()));
        } catch (GitLabApiException e) {
            throw new GitLabMergeRequestException(
                    "failed to open draft MR for " + incident.id().value(), e);
        }
    }

    private List<CommitAction> buildCommitActions(ProposedFix fix) throws GitLabApiException {
        List<CommitAction> actions = new ArrayList<>(fix.patches().size());
        for (FilePatch patch : fix.patches()) {
            CommitAction.Action action =
                    fileExists(patch.path())
                            ? CommitAction.Action.UPDATE
                            : CommitAction.Action.CREATE;
            actions.add(
                    new CommitAction()
                            .withAction(action)
                            .withFilePath(patch.path())
                            .withContent(patch.newContent())
                            .withEncoding(Constants.Encoding.TEXT));
        }
        return actions;
    }

    private boolean fileExists(String path) throws GitLabApiException {
        try {
            RepositoryFile file =
                    gitLab.getRepositoryFileApi()
                            .getFile(properties.projectPath(), path, properties.baseBranch());
            return file != null;
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == 404) {
                return false;
            }
            throw e;
        }
    }

    private static String uniqueBranchName(String suggested, Incident incident) {
        String base = suggested == null || suggested.isBlank() ? "log-triage/fix" : suggested;
        return base + "-" + incident.id().value().toString().substring(0, 8);
    }

    private static String safeTitle(Incident incident, ProposedFix fix) {
        String s = fix.summary();
        if (s == null || s.isBlank()) {
            s = incident.alertName();
        }
        return s.length() > 100 ? s.substring(0, 97) + "..." : s;
    }

    private static String renderDescription(ValidatedFix validated, Incident incident) {
        ProposedFix fix = validated.fix();
        StringBuilder sb = new StringBuilder(1024);
        sb.append("## Diagnostic\n\n").append(fix.rootCause()).append("\n\n");
        sb.append("**Confiance** : ").append(fix.confidence().percent()).append("%\n\n");
        sb.append("**Incident** : ")
                .append(incident.alertName())
                .append(" (")
                .append(incident.service())
                .append(")\n");
        incident.originUrl()
                .ifPresent(u -> sb.append("**Alerte d'origine** : ").append(u).append('\n'));
        incident.traceId().ifPresent(t -> sb.append("**Trace** : ").append(t).append('\n'));
        sb.append("\n## Vérification\n\n```\n")
                .append(truncate(validated.verification().output(), 4000))
                .append("\n```\n");
        sb.append("\n_Cette MR a été ouverte automatiquement en draft. Aucun auto-merge._\n");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n... [truncated]";
    }
}
