///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus.images:jdock-variant-helper:1.0-SNAPSHOT
//DEPS info.picocli:picocli:4.7.4
package io.quarkus.images;

import com.google.common.io.MoreFiles;
import io.quarkus.images.config.Config;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@CommandLine.Command(name = "test")
public class Test implements Callable<Integer> {

    @CommandLine.Option(names = { "--out" }, description = "The output image")
    private String output;

    @CommandLine.Option(names = {
            "--in" }, description = "The YAML file containing the variants", defaultValue = "mandrel.yaml")
    private File in;

    @CommandLine.Option(names = {
            "--dockerfile-dir" }, description = "The location where the docker file should be created", defaultValue = "target/docker")
    private File dockerFileDir;

    @CommandLine.Option(names = { "--ubi-minimal" }, description = "The UBI Minimal base image")
    private String base;

    @CommandLine.Option(names = "--dry-run", description = "Just generate the docker file and skip the container build")
    private boolean dryRun;

    @CommandLine.Option(names = { "--alias" }, description = "An optional alias for the output image (ignored)")
    @Deprecated
    private Optional<String> alias;

    @Override
    public Integer call() throws Exception {
        final Config config = Config.read(output, in);
        final Path tsDir = Path.of("mandrel-integration-tests");
        if (Files.exists(tsDir)) {
            MoreFiles.deleteRecursively(tsDir);
        }
        final List<String> git = List.of("git", "clone", "--branch", "testing-more-runtime-images",
                "https://github.com/Karm/mandrel-integration-tests.git");
        final Process gitProcess = runCommand(git, new File("."));
        gitProcess.waitFor(3, TimeUnit.MINUTES); // Generous. It's a tiny repo.
        if (gitProcess.exitValue() != 0) {
            System.err.println("Failed to clone the mandrel-integration-tests repository.");
            return gitProcess.exitValue();
        }
        for (Config.ImageConfig image : config.images) {
            if (image.isMultiArch()) {
                System.out
                        .println("\uD83D\uDD25\tTesting multi-arch image " + image.fullname(config) + " referencing "
                                + image.getNestedImages(config));
            } else {
                System.out
                        .println("\uD83D\uDD25\tTesting single-arch image " + image.fullname(config));
            }

            // Maven calls JBang and that calls Maven. That Maven calls Maven again. It's Maven all the way down.
            final List<String> testsuite = List.of(
                    "mvn", "clean", "verify",
                    "-Ptestsuite-builder-image",
                    "-Dtest=AppReproducersTest#imageioAWTContainerTest",
                    "-Dquarkus.native.builder-image=" + image.fullname(config),
                    "-Dquarkus.native.container-runtime=docker",
                    "-Drootless.container-runtime=false",
                    "-Ddocker.with.sudo=false");
            final Process testsuiteProcess = runCommand(testsuite, tsDir.toFile());
            testsuiteProcess.waitFor(20, TimeUnit.MINUTES); // We might be downloading 6+ base images on first run.
            if (testsuiteProcess.exitValue() != 0) {
                System.err.println("Failed to run the mandrel-integration-tests.");
                return testsuiteProcess.exitValue();
            }
            // Spit out the log for debugging. File is usually around 20K.
            System.out.println(Files.readString(Path.of("mandrel-integration-tests", "testsuite", "target", "archived-logs",
                    "org.graalvm.tests.integration.AppReproducersTest", "imageioAWTContainerTest", "build-and-run.log")));
        }
        return 0;
    }

    public static Process runCommand(List<String> command, File directory) throws IOException {
        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        final Map<String, String> envA = processBuilder.environment();
        envA.put("PATH", System.getenv("PATH"));
        processBuilder.redirectErrorStream(true)
                .inheritIO()
                .directory(directory);
        return processBuilder.start();
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new Test()).execute(args);
        System.exit(exitCode);
    }
}
