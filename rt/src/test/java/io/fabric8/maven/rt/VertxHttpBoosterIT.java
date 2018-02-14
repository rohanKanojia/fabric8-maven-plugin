/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.fabric8.maven.rt;

import io.fabric8.openshift.api.model.Route;
import okhttp3.Response;
import org.apache.http.HttpStatus;
import org.eclipse.jgit.lib.Repository;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Ignore;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;

public class VertxHttpBoosterIT extends BaseBoosterIT {
    private final String SPRING_BOOT_HTTP_BOOSTER_GIT = "https://github.com/openshiftio-vertx-boosters/vertx-http-booster.git";

    private final String EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL = "fabric8:deploy -Dfabric8.openshift.trimImageInContainerSpec=true", EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE = "openshift";

    private final String RELATIVE_POM_PATH = "/pom.xml";

    private final String testEndpoint = "/api/greeting";

    private final String ANNOTATION_KEY = "vertx-testKey", ANNOTATION_VALUE = "vertx-testValue";

    @Test
    @Ignore
    public void deploy_vertx_app_once() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT, RELATIVE_POM_PATH);

        addRedeploymentAnnotations(testRepository, RELATIVE_POM_PATH, "deploymentType", "deployOnce", fmpConfigurationFile);

        deploy(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        waitTillApplicationPodStarts("deploymentType", "deployOnce");
        TimeUnit.SECONDS.sleep(20);
        assertDeployment();
    }

    @Test
    @Ignore
    public void redeploy_vertx_app() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT, RELATIVE_POM_PATH);

        // change the source code
        updateSourceCode(testRepository, RELATIVE_POM_PATH);
        addRedeploymentAnnotations(testRepository, RELATIVE_POM_PATH, ANNOTATION_KEY, ANNOTATION_VALUE, fmpConfigurationFile);

        // re-deploy
        deploy(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        waitUntilDeployment(true);
        assertDeployment();

        assert checkDeploymentsForAnnotation(ANNOTATION_KEY);
    }

    private void deploy(Repository testRepository, String buildGoal, String buildProfile) throws Exception {
        runEmbeddedMavenBuild(testRepository, buildGoal, buildProfile);
    }

    private void assertDeployment() throws Exception {
        assertThat(openShiftClient).deployment(testsuiteRepositoryArtifactId);
        assertThat(openShiftClient).service(testsuiteRepositoryArtifactId);

        RouteAssert.assertRoute(openShiftClient, testsuiteRepositoryArtifactId);
        assertThatWeServeAsExpected(getApplicationRouteWithName(testsuiteRepositoryArtifactId));
    }

    private void waitUntilDeployment(boolean bIsReployed) throws Exception {
        if(bIsReployed)
            waitTillApplicationPodStarts(ANNOTATION_KEY, ANNOTATION_VALUE);
        else
            waitTillApplicationPodStarts();
    }

    private void assertThatWeServeAsExpected(Route applicationRoute) throws Exception {
        String hostUrl = "http://" + applicationRoute.getSpec().getHost() + testEndpoint;

        int nTries = 0;
        Response readResponse = null;
        do {
            readResponse = makeHttpRequest(HttpRequestType.GET, hostUrl, null);
            nTries++;
            TimeUnit.SECONDS.sleep(10);
        } while(nTries < 3 && readResponse != null && readResponse.code() != HttpStatus.SC_OK);

        String responseContent = readResponse.body().string();
        try {
            assert new JSONObject(responseContent).getString("content").equals("Hello, World!");
        } catch (JSONException jsonException) {
            logger.log(Level.SEVERE, "Unexpected response, expecting json. Actual : " + responseContent);
            logger.log(Level.SEVERE, jsonException.getMessage(), jsonException);
        }
    }

    @After
    public void cleanup() throws Exception {
        cleanSampleTestRepository();
    }
}
