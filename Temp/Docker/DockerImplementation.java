import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.io.*;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Docker Java SDK Implementation Demo
 *
 * DEPENDENCIES (add to pom.xml):
 * <dependency>
 *   <groupId>com.github.docker-java</groupId>
 *   <artifactId>docker-java-core</artifactId>
 *   <version>3.3.4</version>
 * </dependency>
 * <dependency>
 *   <groupId>com.github.docker-java</groupId>
 *   <artifactId>docker-java-transport-httpclient5</artifactId>
 *   <version>3.3.4</version>
 * </dependency>
 *
 * PREREQUISITES:
 *  - Docker Desktop must be running on your machine.
 *  - On Windows: Docker Desktop exposes daemon at tcp://localhost:2375
 *    (enable in Docker Desktop -> Settings -> General ->
 *     "Expose daemon on tcp://localhost:2375 without TLS")
 *
 * This demo covers:
 *  1. Connect to Docker daemon
 *  2. List images and containers
 *  3. Pull an image from Docker Hub
 *  4. Create and start a container
 *  5. Execute a command inside a container
 *  6. Fetch container logs
 *  7. Stop and remove a container
 *  8. Manage volumes
 *  9. Manage networks
 * 10. Build an image from a Dockerfile (in-memory)
 */
public class DockerImplementation {

    // ----------------------------------------------------------------
    // 1. CONNECT TO DOCKER DAEMON
    // ----------------------------------------------------------------
    static DockerClient connectToDocker() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://localhost:2375")   // Windows Docker Desktop
                // On Linux/Mac use: unix:///var/run/docker.sock
                .withDockerTlsVerify(false)
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(URI.create("tcp://localhost:2375"))
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(60))
                .build();

        DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
        System.out.println("[Docker] Connected to Docker daemon.");
        return dockerClient;
    }

    // ----------------------------------------------------------------
    // 2. DOCKER INFO — ping and system info
    // ----------------------------------------------------------------
    static void printDockerInfo(DockerClient client) {
        try {
            client.pingCmd().exec();
            System.out.println("[Docker] Ping successful — daemon is running.");

            Info info = client.infoCmd().exec();
            System.out.println("[Docker] Server Version : " + info.getServerVersion());
            System.out.println("[Docker] Containers     : " + info.getContainers());
            System.out.println("[Docker] Images         : " + info.getImages());
            System.out.println("[Docker] OS             : " + info.getOperatingSystem());
            System.out.println("[Docker] CPUs           : " + info.getNCPU());
            System.out.println("[Docker] Memory         : " + info.getMemTotal() / (1024 * 1024) + " MB");
        } catch (Exception e) {
            System.err.println("[Docker] Cannot connect to Docker daemon: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // 3. LIST IMAGES
    // ----------------------------------------------------------------
    static void listImages(DockerClient client) {
        List<Image> images = client.listImagesCmd().exec();
        System.out.println("\n[Images] Total: " + images.size());
        for (Image img : images) {
            String tags = img.getRepoTags() != null ? Arrays.toString(img.getRepoTags()) : "<none>";
            System.out.printf("  ID=%.12s  Tags=%-40s  Size=%d MB%n",
                    img.getId().replace("sha256:", ""),
                    tags,
                    img.getSize() / (1024 * 1024));
        }
    }

    // ----------------------------------------------------------------
    // 4. LIST CONTAINERS
    // ----------------------------------------------------------------
    static void listContainers(DockerClient client, boolean showAll) {
        List<Container> containers = client.listContainersCmd()
                .withShowAll(showAll)
                .exec();
        System.out.println("\n[Containers] Total: " + containers.size() + (showAll ? " (all)" : " (running)"));
        for (Container c : containers) {
            System.out.printf("  ID=%.12s  Name=%-25s  Image=%-25s  Status=%s%n",
                    c.getId(),
                    Arrays.toString(c.getNames()),
                    c.getImage(),
                    c.getStatus());
        }
    }

    // ----------------------------------------------------------------
    // 5. PULL IMAGE FROM DOCKER HUB
    // ----------------------------------------------------------------
    static void pullImage(DockerClient client, String imageName) throws InterruptedException {
        System.out.println("\n[Pull] Pulling image: " + imageName);
        client.pullImageCmd(imageName)
                .exec(new PullImageResultCallback() {
                    @Override
                    public void onNext(PullResponseItem item) {
                        if (item.getStatus() != null) {
                            System.out.println("[Pull] " + item.getStatus()
                                    + (item.getProgressDetail() != null ? " " + item.getProgress() : ""));
                        }
                        super.onNext(item);
                    }
                })
                .awaitCompletion();
        System.out.println("[Pull] Image pulled successfully: " + imageName);
    }

    // ----------------------------------------------------------------
    // 6. CREATE AND START A CONTAINER
    // ----------------------------------------------------------------
    static String createAndStartContainer(DockerClient client,
                                          String imageName,
                                          String containerName,
                                          int hostPort,
                                          int containerPort) {
        System.out.println("\n[Container] Creating: " + containerName);

        // Port binding: hostPort -> containerPort
        ExposedPort exposedPort    = ExposedPort.tcp(containerPort);
        PortBinding portBinding    = PortBinding.parse(hostPort + ":" + containerPort);
        Ports portBindings         = new Ports();
        portBindings.bind(exposedPort, portBinding.getBinding());

        CreateContainerResponse response = client.createContainerCmd(imageName)
                .withName(containerName)
                .withExposedPorts(exposedPort)
                .withHostConfig(
                    HostConfig.newHostConfig()
                        .withPortBindings(portBindings)
                        .withAutoRemove(false)           // keep container after stop
                )
                .withEnv("NGINX_HOST=localhost", "NGINX_PORT=80")
                .exec();

        String containerId = response.getId();
        System.out.println("[Container] Created with ID: " + containerId.substring(0, 12));

        client.startContainerCmd(containerId).exec();
        System.out.println("[Container] Started: " + containerName + " on port " + hostPort);

        return containerId;
    }

    // ----------------------------------------------------------------
    // 7. INSPECT A CONTAINER
    // ----------------------------------------------------------------
    static void inspectContainer(DockerClient client, String containerId) {
        InspectContainerResponse info = client.inspectContainerCmd(containerId).exec();
        System.out.println("\n[Inspect] Container: " + info.getName());
        System.out.println("[Inspect] Status    : " + info.getState().getStatus());
        System.out.println("[Inspect] Image     : " + info.getConfig().getImage());
        System.out.println("[Inspect] Started At: " + info.getState().getStartedAt());
        if (info.getNetworkSettings() != null && info.getNetworkSettings().getIpAddress() != null) {
            System.out.println("[Inspect] IP Address: " + info.getNetworkSettings().getIpAddress());
        }
    }

    // ----------------------------------------------------------------
    // 8. EXECUTE COMMAND INSIDE A CONTAINER
    // ----------------------------------------------------------------
    static void execInContainer(DockerClient client, String containerId, String... command) throws Exception {
        System.out.println("\n[Exec] Running command in container: " + Arrays.toString(command));

        ExecCreateCmdResponse execCreate = client.execCreateCmd(containerId)
                .withCmd(command)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();

        client.execStartCmd(execCreate.getId())
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        System.out.print("[Exec] " + new String(frame.getPayload()));
                    }
                })
                .awaitCompletion();
    }

    // ----------------------------------------------------------------
    // 9. FETCH CONTAINER LOGS
    // ----------------------------------------------------------------
    static void fetchLogs(DockerClient client, String containerId) throws Exception {
        System.out.println("\n[Logs] Fetching logs for container: " + containerId.substring(0, 12));
        client.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withTail(20)          // last 20 lines
                .withTimestamps(true)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        System.out.print("[Log] " + new String(frame.getPayload()));
                    }
                })
                .awaitCompletion();
    }

    // ----------------------------------------------------------------
    // 10. STOP AND REMOVE A CONTAINER
    // ----------------------------------------------------------------
    static void stopAndRemoveContainer(DockerClient client, String containerId) {
        System.out.println("\n[Container] Stopping: " + containerId.substring(0, 12));
        client.stopContainerCmd(containerId).withTimeout(10).exec();
        System.out.println("[Container] Stopped.");

        client.removeContainerCmd(containerId).withForce(true).exec();
        System.out.println("[Container] Removed.");
    }

    // ----------------------------------------------------------------
    // 11. VOLUME MANAGEMENT
    // ----------------------------------------------------------------
    static void volumeDemo(DockerClient client) {
        String volumeName = "java-demo-volume";

        // Create volume
        CreateVolumeResponse vol = client.createVolumeCmd()
                .withName(volumeName)
                .exec();
        System.out.println("\n[Volume] Created: " + vol.getName() + " at " + vol.getMountpoint());

        // List volumes
        ListVolumesResponse volList = client.listVolumesCmd().exec();
        System.out.println("[Volume] Total volumes: " + volList.getVolumes().size());
        volList.getVolumes().forEach(v ->
                System.out.println("  - " + v.getName() + " | " + v.getMountpoint()));

        // Remove volume
        client.removeVolumeCmd(volumeName).exec();
        System.out.println("[Volume] Removed: " + volumeName);
    }

    // ----------------------------------------------------------------
    // 12. NETWORK MANAGEMENT
    // ----------------------------------------------------------------
    static void networkDemo(DockerClient client) {
        String networkName = "java-demo-network";

        // Create bridge network
        CreateNetworkResponse net = client.createNetworkCmd()
                .withName(networkName)
                .withDriver("bridge")
                .exec();
        System.out.println("\n[Network] Created: " + networkName + " ID=" + net.getId().substring(0, 12));

        // List networks
        List<Network> networks = client.listNetworksCmd().exec();
        System.out.println("[Network] Total networks: " + networks.size());
        networks.forEach(n ->
                System.out.printf("  - %-20s Driver=%-10s ID=%.12s%n",
                        n.getName(), n.getDriver(), n.getId()));

        // Remove network
        client.removeNetworkCmd(net.getId()).exec();
        System.out.println("[Network] Removed: " + networkName);
    }

    // ----------------------------------------------------------------
    // 13. BUILD IMAGE FROM DOCKERFILE (written to temp file)
    // ----------------------------------------------------------------
    static void buildImageDemo(DockerClient client) throws Exception {
        System.out.println("\n[Build] Building custom Docker image...");

        // Write a simple Dockerfile to a temp directory
        File tempDir       = new File(System.getProperty("java.io.tmpdir"), "docker-java-demo");
        tempDir.mkdirs();
        File dockerfile    = new File(tempDir, "Dockerfile");

        try (PrintWriter pw = new PrintWriter(dockerfile)) {
            pw.println("FROM alpine:3.19");
            pw.println("LABEL maintainer=\"java-demo\"");
            pw.println("RUN echo 'Hello from Docker Java SDK build!' > /hello.txt");
            pw.println("CMD [\"cat\", \"/hello.txt\"]");
        }

        // Build image
        String imageId = client.buildImageCmd(tempDir)
                .withTags(java.util.Collections.singleton("java-demo-image:1.0"))
                .exec(new BuildImageResultCallback() {
                    @Override
                    public void onNext(BuildResponseItem item) {
                        if (item.getStream() != null && !item.getStream().isBlank()) {
                            System.out.print("[Build] " + item.getStream());
                        }
                        super.onNext(item);
                    }
                })
                .awaitImageId();

        System.out.println("[Build] Image built successfully. ID: " + imageId.substring(0, Math.min(19, imageId.length())));

        // Run the built image
        CreateContainerResponse resp = client.createContainerCmd("java-demo-image:1.0")
                .withName("java-demo-run")
                .exec();
        client.startContainerCmd(resp.getId()).exec();

        // Fetch its logs
        fetchLogs(client, resp.getId());

        // Cleanup
        stopAndRemoveContainer(client, resp.getId());
        client.removeImageCmd("java-demo-image:1.0").withForce(true).exec();
        System.out.println("[Build] Cleaned up demo image.");

        // Delete temp files
        dockerfile.delete();
        tempDir.delete();
    }

    // ================================================================
    // MAIN — runs all demos
    // ================================================================
    public static void main(String[] args) throws Exception {

        System.out.println("============================================================");
        System.out.println("           DOCKER JAVA SDK IMPLEMENTATION DEMO");
        System.out.println("============================================================");

        DockerClient client = connectToDocker();

        // ----- Demo 1: Docker Info -----
        System.out.println("\n--- Demo 1: Docker System Info ---");
        printDockerInfo(client);

        // ----- Demo 2: List Images -----
        System.out.println("\n--- Demo 2: List Images ---");
        listImages(client);

        // ----- Demo 3: List Containers -----
        System.out.println("\n--- Demo 3: List Containers ---");
        listContainers(client, true);

        // ----- Demo 4: Pull nginx image -----
        System.out.println("\n--- Demo 4: Pull Image (nginx:alpine) ---");
        pullImage(client, "nginx:alpine");

        // ----- Demo 5: Create and Start Container -----
        System.out.println("\n--- Demo 5: Create and Start nginx Container ---");
        String containerId = createAndStartContainer(client,
                "nginx:alpine", "demo-nginx", 8088, 80);
        Thread.sleep(1000);

        // ----- Demo 6: Inspect Container -----
        System.out.println("\n--- Demo 6: Inspect Container ---");
        inspectContainer(client, containerId);

        // ----- Demo 7: Execute Command Inside Container -----
        System.out.println("\n--- Demo 7: Execute Command in Container ---");
        execInContainer(client, containerId, "nginx", "-v");

        // ----- Demo 8: Fetch Logs -----
        System.out.println("\n--- Demo 8: Container Logs ---");
        fetchLogs(client, containerId);

        // ----- Demo 9: Stop and Remove Container -----
        System.out.println("\n--- Demo 9: Stop and Remove Container ---");
        stopAndRemoveContainer(client, containerId);

        // ----- Demo 10: Volume Management -----
        System.out.println("\n--- Demo 10: Volume Management ---");
        volumeDemo(client);

        // ----- Demo 11: Network Management -----
        System.out.println("\n--- Demo 11: Network Management ---");
        networkDemo(client);

        // ----- Demo 12: Build Image from Dockerfile -----
        System.out.println("\n--- Demo 12: Build Image from Dockerfile ---");
        buildImageDemo(client);

        client.close();

        System.out.println("\n============================================================");
        System.out.println("   ALL DOCKER DEMOS COMPLETE");
        System.out.println("============================================================");
    }
}
