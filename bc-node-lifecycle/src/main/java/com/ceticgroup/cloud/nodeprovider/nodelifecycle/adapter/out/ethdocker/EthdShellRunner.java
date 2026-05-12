package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface EthdShellRunner {

    void ensureCache(Path cacheDir, String repoUrl) throws IOException;

    void cloneIntoWorkdir(Path cacheDir, String tag, Path workdir) throws IOException;

    Map<String, String> readDefaultEnv(Path workdir) throws IOException;

    void writeEnv(Path workdir, String envContent) throws IOException;

    void writeFile(Path workdir, String name, String content) throws IOException;

    void runEthdUp(Path workdir) throws IOException;

    void runEthdDown(Path workdir) throws IOException;

    void runEthdTerminate(Path workdir) throws IOException;

    void runEthdKeysImport(Path workdir, String keystorePassword) throws IOException;

    void removeWorkdir(Path workdir) throws IOException;

    /**
     * Creates the host-side EL datadir and chowns it to the eth-docker container UID so the EL
     * process can write to the bind mount. Idempotent.
     */
    void ensureDataDir(Path dataDir, int ownerUid) throws IOException;

    /** Removes the host-side EL datadir recursively. Idempotent. */
    void removeDataDir(Path dataDir) throws IOException;
}
