/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.instrumentation.resources.ContainerResource.buildResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ContainerResourceTest {


  static Stream<Arguments> invalidIDs(){
    return Stream.of(
        // invalid containerId (non-hex)
        arguments("13:name=systemd:/podruntime/docker/kubepods/ac679f8a8319c8cf7d38e1adf263bc08d23zzzz",false),
            // unrecognized format (last "-" is after last ".")
            arguments("13:name=systemd:/podruntime/docker/kubepods/ac679f8.a8319c8cf7d38e1adf263bc08-d23zzzz",false)
    );
  }
  @ParameterizedTest
  @MethodSource("invalidIDs")
  void buildResource_returnsEmptyResource_whenContainerIdIsInvalid(
      String line, Boolean cgroupv2, @TempDir Path tempFolder) throws IOException {
    Path cgroup = createCgroup(tempFolder.resolve("cgroup"), line);
    assertThat(buildResource(cgroup,cgroupv2)).isEqualTo(Resource.empty());
  }

  @Test
  void buildResource_returnsEmptyResource_whenFileDoesNotExist(@TempDir Path tempFolder) {
    Path cgroup = tempFolder.resolve("DoesNotExist");
    assertThat(buildResource(cgroup,false)).isEqualTo(Resource.empty());
  }

  @ParameterizedTest
  @MethodSource("validLines")
  void buildResource_extractsContainerIdFromValidLines(
      String line, String expectedContainerId,Boolean cgroupv2,@TempDir Path tempFolder) throws IOException {
    Path cgroup = createCgroup(tempFolder.resolve("cgroup"), line);
    assertThat(getContainerId(buildResource(cgroup,cgroupv2))).isEqualTo(expectedContainerId);
  }

  static Stream<Arguments> validLines() {
    return Stream.of(
        // with suffix
        arguments(
            "13:name=systemd:/podruntime/docker/kubepods/ac679f8a8319c8cf7d38e1adf263bc08d23.aaaa",
            "ac679f8a8319c8cf7d38e1adf263bc08d23",false),
        // with prefix and suffix
        arguments(
            "13:name=systemd:/podruntime/docker/kubepods/crio-dc679f8a8319c8cf7d38e1adf263bc08d23.stuff",
            "dc679f8a8319c8cf7d38e1adf263bc08d23",false),
        // just container id
        arguments(
            "13:name=systemd:/pod/d86d75589bf6cc254f3e2cc29debdf85dde404998aa128997a819ff991827356",
            "d86d75589bf6cc254f3e2cc29debdf85dde404998aa128997a819ff991827356",false),
        // with prefix
        arguments(
            "//\n"
                + "1:name=systemd:/podruntime/docker/kubepods/docker-dc579f8a8319c8cf7d38e1adf263bc08d23"
                + "2:name=systemd:/podruntime/docker/kubepods/docker-dc579f8a8319c8cf7d38e1adf263bc08d23"
                + "3:name=systemd:/podruntime/docker/kubepods/docker-dc579f8a8319c8cf7d38e1adf263bc08d23",
            "dc579f8a8319c8cf7d38e1adf263bc08d23",false),
        // with two dashes in prefix
        arguments(
            "11:perf_event:/kubepods.slice/kubepods-burstable.slice/kubepods-burstable-pod4415fd05_2c0f_4533_909b_f2180dca8d7c.slice/cri-containerd-713a77a26fe2a38ebebd5709604a048c3d380db1eb16aa43aca0b2499e54733c.scope",
            "713a77a26fe2a38ebebd5709604a048c3d380db1eb16aa43aca0b2499e54733c",false),
        // with colon, env: k8s v1.24.0, the cgroupDriver by systemd(default), and container is
        // cri-containerd v1.6.8
        arguments(
            "11:devices:/system.slice/containerd.service/kubepods-pod87a18a64_b74a_454a_b10b_a4a36059d0a3.slice:cri-containerd:05c48c82caff3be3d7f1e896981dd410e81487538936914f32b624d168de9db0",
            "05c48c82caff3be3d7f1e896981dd410e81487538936914f32b624d168de9db0",false),

        //cgroupv2 Ubuntu docker container
        arguments("481 480 0:156 / /dev/pts rw,nosuid,noexec,relatime - devpts devpts rw,gid=5,mode=620,ptmxmode=666\n"
            + "482 478 0:157 / /sys ro,nosuid,nodev,noexec,relatime - sysfs sysfs ro\n"
            + "483 482 0:30 / /sys/fs/cgroup ro,nosuid,nodev,noexec,relatime - cgroup2 cgroup rw\n"
            + "492 480 0:153 / /dev/mqueue rw,nosuid,nodev,noexec,relatime - mqueue mqueue rw\n"
            + "493 480 0:158 / /dev/shm rw,nosuid,nodev,noexec,relatime - tmpfs shm rw,size=65536k\n"
            + "494 478 8:1 /docker/containers/33e0d47f6d28e715b4b3e7bc3a445ecdd106ef3a8519e6e654c55ce465894886/resolv.conf /etc/resolv.conf rw,relatime - ext4 /dev/sda1 rw\n"
            + "495 478 8:1 /docker/containers/33e0d47f6d28e715b4b3e7bc3a445ecdd106ef3a8519e6e654c55ce465894886/hostname /etc/hostname rw,relatime - ext4 /dev/sda1 rw\n"
            + "496 478 8:1 /docker/containers/33e0d47f6d28e715b4b3e7bc3a445ecdd106ef3a8519e6e654c55ce465894886/hosts /etc/hosts rw,relatime - ext4 /dev/sda1 rw\n"
            + "389 480 0:156 /0 /dev/console rw,nosuid,noexec,relatime - devpts devpts rw,gid=5,mode=620,ptmxmode=666\n"
            + "390 479 0:154 /bus /proc/bus ro,nosuid,nodev,noexec,relatime - proc proc rw\n"
            + "391 479 0:154 /fs /proc/fs ro,nosuid,nodev,noexec,relatime - proc proc rw","33e0d47f6d28e715b4b3e7bc3a445ecdd106ef3a8519e6e654c55ce465894886",true));

  }

  private static String getContainerId(Resource resource) {
    return resource.getAttribute(ResourceAttributes.CONTAINER_ID);
  }

  private static Path createCgroup(Path path, String line) throws IOException {
    return Files.write(path, line.getBytes(StandardCharsets.UTF_8));
  }
}
