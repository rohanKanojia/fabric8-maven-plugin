/**
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
package io.fabric8.maven.enricher.fabric8;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.maven.core.handler.DeploymentHandler;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import mockit.Mocked;
import org.apache.maven.project.MavenProject;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Tests that the enrichment is performed on the right containers.
 *
 * @author Nicola
 */
public class AbstractHealthCheckEnricherTest {

    @Mocked
    Logger log;

    @Mocked
    MavenEnricherContext enricherContext;

    @Test
    @Ignore
    public void enrichSingleContainer() {
        KubernetesListBuilder list = new KubernetesListBuilder()
                .addNewDeploymentItem()
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .addNewContainer()
                                    .withName("app")
                                    .withImage("app:latest")
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endDeploymentItem();

        createEnricher(new Properties()).addMissingResources(list);

        final AtomicInteger containerFound = new AtomicInteger(0);
        list.accept(new TypedVisitor<ContainerBuilder>() {
            @Override
            public void visit(ContainerBuilder container) {
                assertNotNull(container.build().getLivenessProbe());
                assertNotNull(container.build().getReadinessProbe());
                containerFound.incrementAndGet();
            }
        });

        assertEquals(1, containerFound.get());
    }

    @Test
    @Ignore
    public void enrichContainerWithSidecar() {
        KubernetesListBuilder list = new KubernetesListBuilder()
                .addNewDeploymentItem()
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .addNewContainer()
                                    .withName("app")
                                    .withImage("app:latest")
                                    .addNewEnv()
                                        .withName("FABRIC8_GENERATED")
                                        .withValue("true")
                                    .endEnv()
                                .endContainer()
                                .addNewContainer()
                                    .withName("sidecar")
                                    .withImage("sidecar:latest")
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endDeploymentItem();

        createEnricher(new Properties()).addMissingResources(list);

        final AtomicInteger containerFound = new AtomicInteger(0);
        list.accept(new TypedVisitor<ContainerBuilder>() {
            @Override
            public void visit(ContainerBuilder container) {
                if (container.getName().equals("app")) {
                    assertNotNull(container.build().getLivenessProbe());
                    assertNotNull(container.build().getReadinessProbe());
                    containerFound.incrementAndGet();
                } else if (container.getName().equals("sidecar")) {
                    assertNull(container.build().getLivenessProbe());
                    assertNull(container.build().getReadinessProbe());
                    containerFound.incrementAndGet();
                }
            }
        });

        assertEquals(2, containerFound.get());
    }

    @Test
    @Ignore
    public void enrichSpecificContainers() {

        final Properties properties = new Properties();
        properties.put("fabric8.enricher.basic.enrichContainers", "app2,app3");

        KubernetesListBuilder list = new KubernetesListBuilder()
                .addNewDeploymentItem()
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .addNewContainer()
                                    .withName("app")
                                    .withImage("app:latest")
                                    .addNewEnv()
                                        .withName("FABRIC8_GENERATED")
                                        .withValue("true")
                                    .endEnv()
                                .endContainer()
                                .addNewContainer()
                                    .withName("app2")
                                    .withImage("app2:latest")
                                .endContainer()
                                .addNewContainer()
                                    .withName("app3")
                                    .withImage("app3:latest")
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endDeploymentItem();

        createEnricher(properties).addMissingResources(list);

        final AtomicInteger containerFound = new AtomicInteger(0);
        list.accept(new TypedVisitor<ContainerBuilder>() {
            @Override
            public void visit(ContainerBuilder container) {
                if (container.getName().equals("app")) {
                    assertNull(container.build().getLivenessProbe());
                    assertNull(container.build().getReadinessProbe());
                    containerFound.incrementAndGet();
                } else if (container.getName().equals("app2")) {
                    assertNotNull(container.build().getLivenessProbe());
                    assertNotNull(container.build().getReadinessProbe());
                    containerFound.incrementAndGet();
                } else if (container.getName().equals("app3")) {
                    assertNotNull(container.build().getLivenessProbe());
                    assertNotNull(container.build().getReadinessProbe());
                    containerFound.incrementAndGet();
                }
            }
        });

        assertEquals(3, containerFound.get());
    }

    @Test
    @Ignore
    public void enrichAllContainers() {

        final Properties properties = new Properties();
        properties.put("fabric8.enricher.basic.enrichAllContainers", "true");

        KubernetesListBuilder list = new KubernetesListBuilder()
                .addNewDeploymentItem()
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .addNewContainer()
                                    .withName("app")
                                    .withImage("app:latest")
                                    .addNewEnv()
                                        .withName("FABRIC8_GENERATED")
                                        .withValue("true")
                                    .endEnv()
                                .endContainer()
                                .addNewContainer()
                                    .withName("app2")
                                    .withImage("app2:latest")
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endDeploymentItem();

        createEnricher(properties).addMissingResources(list);

        final AtomicInteger containerFound = new AtomicInteger(0);
        list.accept(new TypedVisitor<ContainerBuilder>() {
            @Override
            public void visit(ContainerBuilder container) {
                if (container.getName().equals("app")) {
                    assertNotNull(container.build().getLivenessProbe());
                    assertNotNull(container.build().getReadinessProbe());
                    containerFound.incrementAndGet();
                } else if (container.getName().equals("app2")) {
                    assertNotNull(container.build().getLivenessProbe());
                    assertNotNull(container.build().getReadinessProbe());
                    containerFound.incrementAndGet();
                }
            }
        });

        assertEquals(2, containerFound.get());
    }

    protected AbstractHealthCheckEnricher createEnricher(Properties properties) {

        MavenProject project = new MavenProject();
        project.getProperties().putAll(properties);

        EnricherContext context = new MavenEnricherContext.Builder()
                .project(project)
                .log(log)
                .build();

        AbstractHealthCheckEnricher enricher = new AbstractHealthCheckEnricher(context, "basic") {
            @Override
            protected Probe getLivenessProbe() {
                return getReadinessProbe();
            }

            @Override
            protected Probe getReadinessProbe() {
                return new ProbeBuilder()
                        .withNewHttpGet()
                        .withHost("localhost")
                        .withNewPort(8080)
                        .endHttpGet()
                        .build();
            }
        };
        return enricher;
    }

}
