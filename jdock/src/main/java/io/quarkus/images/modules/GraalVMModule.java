package io.quarkus.images.modules;

import io.quarkus.images.BuildContext;
import io.quarkus.images.artifacts.Artifact;
import io.quarkus.images.commands.*;

import java.util.List;

public class GraalVMModule extends AbstractModule {
    public static final String GRAALVM_HOME = "/opt/graalvm";
    private final String url;
    private final String sha;
    private final String filename;

    private static final String TEMPLATE = """
            tar xzf %s -C /opt \\
              && mv /opt/graalvm-ce-*-%s* /opt/graalvm \\
              && %s/bin/gu --auto-yes install native-image \\
              && rm -Rf %s""";

    private static final String NEW_TEMPLATE = """
            tar xzf %s -C /opt \\
              && mv /opt/graalvm-community-openjdk-%s* /opt/graalvm \\
              && rm -Rf %s""";
    private final String graalvmVersion;

    public GraalVMModule(String version, String arch, String javaVersion, String sha) {
        super("graalvm",
                version == null ? "jdk-" + javaVersion + "-" + arch
                        : arch != null ? version + "-java" + javaVersion + "-" + arch
                                : version + "-java" + javaVersion + "-amd64");

        if (arch == null) {
            arch = "amd64";
        } else if (arch.equalsIgnoreCase("arm64")) {
            arch = "aarch64";
        } else if (version == null) {
            arch = "x64";
        }

        // local file name:
        if (version != null) {
            this.filename = "graalvm-java-%s-linux-%s-%s.tar.gz"
                    .formatted(javaVersion, arch, version);
        } else {
            this.filename = "graalvm-jdk-%s-linux-%s.tar.gz"
                    .formatted(javaVersion, arch);
        }

        // jdk-20.0.1/graalvm-community-jdk-20.0.1_linux-x64_bin.tar.gz
        // vm -> jdk
        // the version is a 3 digit version
        // graalvm-ce-java -> graalvm-community-jdk
        // .tar.gz -> _bin.tar.gz

        //graalvm version null...
        // https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-20.0.1/graalvm-community-jdk-20.0.1_linux-x64_bin.tar.gz
        // https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-20.0.1/graalvm-community-jdk-20.0.1_linux-aarch64_bin.tar.gz

        if (version != null) {
            this.url = "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-%s/graalvm-ce-java%s-linux-%s-%s.tar.gz"
                    .formatted(version, javaVersion, arch, version);
        } else {
            this.url = "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-%s/graalvm-community-jdk-%s_linux-%s_bin.tar.gz"
                    .formatted(javaVersion, javaVersion, arch);
        }
        this.sha = sha;
        this.graalvmVersion = version == null ? javaVersion : version;
    }

    @Override
    public List<Command> commands(BuildContext bc) {
        Artifact artifact = bc.addArtifact(new Artifact(filename, url, sha));
        String script;
        if (version == null) {
            script = TEMPLATE.formatted(
                    "/tmp/" + artifact.name, // tar
                    graalvmVersion,
                    GRAALVM_HOME, // gu
                    "/tmp/" + artifact.name); // rm
        } else {
            script = NEW_TEMPLATE.formatted(
                    "/tmp/" + artifact.name, // tar
                    graalvmVersion,
                    "/tmp/" + artifact.name); // rm
        }

        return List.of(
                new EnvCommand("JAVA_HOME", GRAALVM_HOME, "GRAALVM_HOME", GRAALVM_HOME),
                new MicrodnfCommand("fontconfig", "freetype-devel"),
                new CopyCommand(artifact, "/tmp/" + artifact.name),
                new RunCommand(script));
    }
}
