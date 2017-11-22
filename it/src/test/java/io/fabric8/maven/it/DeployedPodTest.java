package io.fabric8.maven.it;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.arquillian.smart.testing.rules.git.GitCloner;
import org.eclipse.jgit.lib.Repository;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.BuiltProject;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;

public class DeployedPodTest {
    private final String testRepositoryUrl = "https://github.com/rohanKanojia/fmp-test-repository.git";

    private final String fabric8PluginGroupId = "io.fabric8";

    private final String fabric8PluginArtifactId = "fabric8-maven-plugin";

    private GitCloner gitCloner;

    private Model getCurrentProjectModel() throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader("pom.xml"));
        return model;
    }

    private Repository cloneRepositoryUsingHttp() throws Exception {
        gitCloner = new GitCloner(testRepositoryUrl);
        return gitCloner.cloneRepositoryToTempFolder();
    }

    private void modifyPomFileToProjectVersion(Repository aRepository) throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        String baseDir = aRepository.getWorkTree().getAbsolutePath();
        Model model = reader.read(new FileInputStream(new File(baseDir, "/pom.xml")));

        Map<String, Plugin> aStringToPluginMap = model.getBuild().getPluginsAsMap();
        aStringToPluginMap.get(fabric8PluginGroupId + ":" + fabric8PluginArtifactId).setVersion(getCurrentProjectModel().getVersion());

        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(new FileOutputStream(new File(baseDir, "/pom.xml")), model);
        model.getArtifactId();
    }

    private void runEmbeddedMavenBuild(Repository sampleRepository) {
        String baseDir = sampleRepository.getWorkTree().getAbsolutePath();
        BuiltProject builtProject = EmbeddedMaven.forProject(baseDir + "/pom.xml")
                .setGoals("fabric8:deploy")
                .build();

        assert builtProject.getDefaultBuiltArchive() != null;
    }

    private Repository setupSampleTestRepository() throws Exception {
        Repository aRepository = cloneRepositoryUsingHttp();
        modifyPomFileToProjectVersion(aRepository);
        return aRepository;
    }

    private void cleanSampleTestRepository() throws Exception {
        gitCloner.removeClone();
    }

    @Test
    public void testMain() throws Exception {
        //  1.Clone a sample repository and change it's fmp's version to
        //  current project's version.
        Repository testRepository = setupSampleTestRepository();

        // 2.Run fabric8:deploy goal in order to deploy application on
        //   Kubernetes/Openshift
        runEmbeddedMavenBuild(testRepository);

        // 3.Connect to cluster and verify application pods
        KubernetesClient client = new DefaultKubernetesClient(new ConfigBuilder().build());
        assertThat(client).pods().runningStatus().filterLabel("provider", "fabric8").assertSize().isGreaterThan(0);

        // 4.Clean up the repository
        cleanSampleTestRepository();
    }
}
