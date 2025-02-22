/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class CassandraNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<CassandraRequest, ExecutionInfo> {

  @Override
  @Nullable
  public String transport(CassandraRequest request, @Nullable ExecutionInfo executionInfo) {
    return null;
  }

  @Nullable
  @Override
  public String peerName(CassandraRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Integer peerPort(CassandraRequest request) {
    return null;
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(
      CassandraRequest request, @Nullable ExecutionInfo executionInfo) {
    if (executionInfo == null) {
      return null;
    }
    Node coordinator = executionInfo.getCoordinator();
    if (coordinator == null) {
      return null;
    }
    // resolve() returns an existing InetSocketAddress, it does not do a dns resolve,
    // at least in the only current EndPoint implementation (DefaultEndPoint)
    SocketAddress address = coordinator.getEndPoint().resolve();
    return address instanceof InetSocketAddress ? (InetSocketAddress) address : null;
  }
}
