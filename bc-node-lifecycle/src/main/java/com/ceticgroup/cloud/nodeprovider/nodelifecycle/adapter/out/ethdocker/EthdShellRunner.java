package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import java.io.IOException;
import java.nio.file.Path;

public interface EthdShellRunner {

    void ensureCache(Path cacheDir, String repoUrl) throws IOException;

    void cloneIntoWorkdir(Path cacheDir, String tag, Path workdir) throws IOException;

    void writeEnv(Path workdir, String envContent) throws IOException;

    void runEthdUp(Path workdir) throws IOException;

    void runEthdDown(Path workdir) throws IOException;

    void runEthdTerminate(Path workdir) throws IOException;

    void removeWorkdir(Path workdir) throws IOException;
}
