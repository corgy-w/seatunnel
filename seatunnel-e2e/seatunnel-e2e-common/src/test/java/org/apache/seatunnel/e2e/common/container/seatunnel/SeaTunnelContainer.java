/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.e2e.common.container.seatunnel;

import org.apache.seatunnel.common.utils.FileUtils;
import org.apache.seatunnel.e2e.common.container.AbstractTestContainer;
import org.apache.seatunnel.e2e.common.container.ContainerExtendedFactory;
import org.apache.seatunnel.e2e.common.container.TestContainer;
import org.apache.seatunnel.e2e.common.container.TestContainerId;
import org.apache.seatunnel.e2e.common.util.ContainerUtil;

import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerLoggerFactory;
import org.testcontainers.utility.MountableFile;

import com.google.auto.service.AutoService;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.apache.seatunnel.e2e.common.util.ContainerUtil.PROJECT_ROOT_PATH;

@NoArgsConstructor
@Slf4j
@AutoService(TestContainer.class)
public class SeaTunnelContainer extends AbstractTestContainer {
    protected static final String JDK_DOCKER_IMAGE = "openjdk:8";
    private static final String CLIENT_SHELL = "seatunnel.sh";
    protected static final String SERVER_SHELL = "seatunnel-cluster.sh";
    private static final String LICENSE_PATH = "/etc/seatunnel/whaletunnel.license";
    protected GenericContainer<?> server;

    @Override
    public void startUp() throws Exception {
        server = createSeaTunnelServer();
    }

    private GenericContainer<?> createSeaTunnelServer() throws IOException, InterruptedException {
        GenericContainer<?> server =
                new GenericContainer<>(getDockerImage())
                        .withNetwork(NETWORK)
                        .withEnv("TZ", "UTC")
                        .withCommand(
                                ContainerUtil.adaptPathForWin(
                                        Paths.get(SEATUNNEL_HOME, "bin", SERVER_SHELL).toString()))
                        .withNetworkAliases("server")
                        .withExposedPorts()
                        .withLogConsumer(
                                new Slf4jLogConsumer(
                                        DockerLoggerFactory.getLogger(
                                                "seatunnel-engine:" + JDK_DOCKER_IMAGE)))
                        .waitingFor(Wait.forListeningPort());
        copySeaTunnelStarterToContainer(server);
        server.setExposedPorts(Arrays.asList(5801));
        server.withCopyFileToContainer(
                MountableFile.forHostPath(
                        PROJECT_ROOT_PATH
                                + "/seatunnel-e2e/seatunnel-engine-e2e/connector-seatunnel-e2e-base/src/test/resources/"),
                Paths.get(SEATUNNEL_HOME, "config").toString());

        server.withCopyFileToContainer(
                MountableFile.forHostPath(
                        PROJECT_ROOT_PATH
                                + "/seatunnel-shade/seatunnel-hadoop3-3.3.6-uber/target/seatunnel-hadoop3-3.3.6-uber.jar"),
                Paths.get(SEATUNNEL_HOME, "starter/zeta/seatunnel-hadoop3-3.3.6-uber.jar")
                        .toString());

        server.withCopyFileToContainer(
                MountableFile.forHostPath(
                        PROJECT_ROOT_PATH
                                + "/seatunnel-e2e/seatunnel-engine-e2e/connector-seatunnel-e2e-base/src/test/resources/whaletunnel.license"),
                "/etc/seatunnel/whaletunnel.license");
        // execute extra commands
        executeExtraCommands(server);

        server.start();
        server.execInContainer("mkdir", "-p", "/tmp/seatunnel/plugins");
        return server;
    }

    protected GenericContainer<?> createSeaTunnelContainerWithFakeSourceAndInMemorySink(
            String configFilePath) throws IOException, InterruptedException {
        GenericContainer<?> server =
                new GenericContainer<>(getDockerImage())
                        .withNetwork(NETWORK)
                        .withEnv("TZ", "UTC")
                        .withCommand(
                                ContainerUtil.adaptPathForWin(
                                        Paths.get(SEATUNNEL_HOME, "bin", SERVER_SHELL).toString()))
                        .withNetworkAliases("server")
                        .withExposedPorts()
                        .withLogConsumer(
                                new Slf4jLogConsumer(
                                        DockerLoggerFactory.getLogger(
                                                "seatunnel-engine:" + JDK_DOCKER_IMAGE)))
                        .waitingFor(Wait.forListeningPort());
        copySeaTunnelStarterToContainer(server);
        server.setPortBindings(Collections.singletonList("5801:5801"));
        server.setExposedPorts(Collections.singletonList(5801));

        server.withCopyFileToContainer(
                MountableFile.forHostPath(
                        PROJECT_ROOT_PATH
                                + "/seatunnel-e2e/seatunnel-engine-e2e/connector-seatunnel-e2e-base/src/test/resources/"),
                Paths.get(SEATUNNEL_HOME, "config").toString());

        server.withCopyFileToContainer(
                MountableFile.forHostPath(configFilePath),
                Paths.get(SEATUNNEL_HOME, "config", "seatunnel.yaml").toString());

        server.withCopyFileToContainer(
                MountableFile.forHostPath(
                        PROJECT_ROOT_PATH
                                + "/seatunnel-shade/seatunnel-hadoop3-3.3.6-uber/target/seatunnel-hadoop3-3.3.6-uber.jar"),
                Paths.get(SEATUNNEL_HOME, "starter/zeta/seatunnel-hadoop3-3.3.6-uber.jar")
                        .toString());

        server.withCopyFileToContainer(
                MountableFile.forHostPath(
                        PROJECT_ROOT_PATH
                                + "/seatunnel-e2e/seatunnel-engine-e2e/connector-seatunnel-e2e-base/src/test/resources/whaletunnel.license"),
                "/etc/seatunnel/whaletunnel.license");

        server.start();
        // execute extra commands
        executeExtraCommands(server);

        File module = new File(PROJECT_ROOT_PATH + File.separator + getConnectorModulePath());
        List<File> connectorFiles =
                ContainerUtil.getConnectorFiles(
                        module, Collections.singleton("connector-fake"), getConnectorNamePrefix());
        URL url =
                FileUtils.searchJarFiles(
                                Paths.get(
                                        PROJECT_ROOT_PATH
                                                + File.separator
                                                + "seatunnel-e2e/seatunnel-e2e-common/target"))
                        .stream()
                        .filter(jar -> jar.toString().endsWith("-tests.jar"))
                        .findFirst()
                        .get();
        connectorFiles.add(new File(url.getFile()));
        connectorFiles.forEach(
                jar ->
                        server.copyFileToContainer(
                                MountableFile.forHostPath(jar.getAbsolutePath()),
                                Paths.get(SEATUNNEL_HOME, "connectors", jar.getName()).toString()));
        server.copyFileToContainer(
                MountableFile.forHostPath(
                        PROJECT_ROOT_PATH
                                + "/seatunnel-e2e/seatunnel-engine-e2e/connector-seatunnel-e2e-base/src/test/resources/fake-and-inmemory/plugin-mapping.properties"),
                Paths.get(SEATUNNEL_HOME, "connectors", "plugin-mapping.properties").toString());
        return server;
    }

    @Override
    public void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Override
    protected String getDockerImage() {
        return JDK_DOCKER_IMAGE;
    }

    @Override
    protected String getStartModuleName() {
        return "seatunnel-starter";
    }

    @Override
    protected String getStartShellName() {
        return CLIENT_SHELL;
    }

    @Override
    protected String getConnectorModulePath() {
        return "seatunnel-connectors-v2";
    }

    @Override
    protected String getConnectorType() {
        return "seatunnel";
    }

    @Override
    protected String getConnectorNamePrefix() {
        return "connector-";
    }

    @Override
    protected List<String> getExtraStartShellCommands() {
        return Collections.emptyList();
    }

    @Override
    public TestContainerId identifier() {
        return TestContainerId.SEATUNNEL;
    }

    @Override
    protected String getSavePointCommand() {
        return "-s";
    }

    @Override
    protected String getRestoreCommand() {
        return "-r";
    }

    @Override
    public void executeExtraCommands(ContainerExtendedFactory extendedFactory)
            throws IOException, InterruptedException {
        extendedFactory.extend(server);
    }

    @Override
    public Container.ExecResult executeJob(String confFile)
            throws IOException, InterruptedException {
        log.info("test in container: {}", identifier());
        return executeJob(server, confFile);
    }

    @Override
    public Container.ExecResult savepointJob(String jobId)
            throws IOException, InterruptedException {
        return savepointJob(server, jobId);
    }

    @Override
    public Container.ExecResult restoreJob(String confFile, String jobId)
            throws IOException, InterruptedException {
        return restoreJob(server, confFile, jobId);
    }

    @Override
    public String getServerLogs() {
        return server.getLogs();
    }
}
