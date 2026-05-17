package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Pulls the pinned deposit-cli image asynchronously at startup so the first user-triggered key
 * generation does not also pay the cold pull (~200 MB, 30-60 s on a fresh host).
 */
public final class DepositCliImageWarmer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DepositCliImageWarmer.class);

    private final DockerClient dockerClient;
    private final String image;

    public DepositCliImageWarmer(DockerClient dockerClient, String image) {
        this.dockerClient = dockerClient;
        this.image = image;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(this::pullQuietly, "deposit-cli-warmer");
        t.setDaemon(true);
        t.start();
    }

    private void pullQuietly() {
        long startedAt = System.currentTimeMillis();
        try {
            log.info("pre-pulling deposit-cli image {} in background", image);
            dockerClient
                    .pullImageCmd(image)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion(10, TimeUnit.MINUTES);
            log.info(
                    "deposit-cli image {} ready ({} ms)",
                    image,
                    System.currentTimeMillis() - startedAt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("interrupted while pre-pulling deposit-cli image {}", image);
        } catch (RuntimeException e) {
            // Non-fatal: the first user request will retry implicitly via `docker run`.
            log.warn("failed to pre-pull deposit-cli image {}: {}", image, e.getMessage());
        }
    }
}
