/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.AuthTagRepository;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTable;

/**
 * Performs {@link Field#SESSION_LOOKUP} request. Looks up for Node session based on NodeId, which
 * should be in request field and stores it in {@link Field#SESSION} field.
 */
public class NodeIdToSession implements EnvelopeHandler {
  private static final int SESSION_CLEANUP_DELAY_SECONDS = 180;
  private static final int REQUEST_CLEANUP_DELAY_SECONDS = 60;
  private static final Logger logger = LogManager.getLogger(NodeIdToSession.class);
  private final LocalNodeRecordStore localNodeRecordStore;
  private final Bytes staticNodeKey;
  private final NodeBucketStorage nodeBucketStorage;
  private final AuthTagRepository authTagRepo;
  private final Map<SessionKey, NodeSession> recentSessions = new ConcurrentHashMap<>();
  private final NodeTable nodeTable;
  private final Pipeline outgoingPipeline;
  private final ExpirationScheduler<SessionKey> sessionExpirationScheduler;
  private final ExpirationScheduler<Bytes> requestExpirationScheduler;

  public NodeIdToSession(
      LocalNodeRecordStore localNodeRecordStore,
      Bytes staticNodeKey,
      NodeBucketStorage nodeBucketStorage,
      AuthTagRepository authTagRepo,
      NodeTable nodeTable,
      Pipeline outgoingPipeline,
      ExpirationSchedulerFactory expirationSchedulerFactory) {
    this.localNodeRecordStore = localNodeRecordStore;
    this.staticNodeKey = staticNodeKey;
    this.nodeBucketStorage = nodeBucketStorage;
    this.authTagRepo = authTagRepo;
    this.nodeTable = nodeTable;
    this.outgoingPipeline = outgoingPipeline;
    this.sessionExpirationScheduler =
        expirationSchedulerFactory.create(SESSION_CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
    this.requestExpirationScheduler =
        expirationSchedulerFactory.create(REQUEST_CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        "Envelope {} in NodeIdToSession, checking requirements satisfaction", envelope.getId());
    if (!HandlerUtil.requireField(Field.SESSION_LOOKUP, envelope)) {
      return;
    }
    logger.trace("Envelope {} in NodeIdToSession, requirements are satisfied!", envelope.getId());

    SessionLookup sessionRequest = (SessionLookup) envelope.get(Field.SESSION_LOOKUP);
    envelope.remove(Field.SESSION_LOOKUP);
    logger.trace(
        "Envelope {}: Session lookup requested for nodeId {}",
        envelope.getId(),
        sessionRequest.getNodeId());
    getOrCreateSession(sessionRequest.getNodeId(), envelope)
        .ifPresentOrElse(
            nodeSession -> {
              envelope.put(Field.SESSION, nodeSession);
              logger.trace("Session resolved: {} in envelope #{}", nodeSession, envelope.getId());
            },
            () ->
                logger.trace(
                    "Session could not be resolved or created for {}", sessionRequest.getNodeId()));
  }

  private Optional<NodeSession> getOrCreateSession(Bytes nodeId, Envelope envelope) {
    return getRemoteSocketAddress(envelope)
        .map(
            remoteSocketAddress -> {
              SessionKey sessionKey = new SessionKey(nodeId, remoteSocketAddress);
              NodeSession context =
                  recentSessions.computeIfAbsent(sessionKey, this::createNodeSession);

              sessionExpirationScheduler.put(
                  sessionKey,
                  () -> {
                    recentSessions.remove(sessionKey);
                    context.cleanup();
                  });
              return context;
            });
  }

  private NodeSession createNodeSession(final SessionKey key) {
    Optional<NodeRecord> nodeRecord = nodeTable.getNode(key.nodeId).map(NodeRecordInfo::getNode);
    SecureRandom random = new SecureRandom();
    return new NodeSession(
        key.nodeId,
        nodeRecord,
        key.remoteSocketAddress,
        localNodeRecordStore,
        staticNodeKey,
        nodeTable,
        nodeBucketStorage,
        authTagRepo,
        outgoingPipeline::push,
        random,
        requestExpirationScheduler);
  }

  private Optional<InetSocketAddress> getRemoteSocketAddress(final Envelope envelope) {
    return Optional.ofNullable((InetSocketAddress) envelope.get(Field.REMOTE_SENDER))
        .or(() -> ((NodeRecord) envelope.get(Field.NODE)).getUdpAddress());
  }

  private static class SessionKey {
    private final Bytes nodeId;
    private final InetSocketAddress remoteSocketAddress;

    private SessionKey(final Bytes nodeId, final InetSocketAddress remoteSocketAddress) {
      checkNotNull(remoteSocketAddress);
      this.nodeId = nodeId;
      this.remoteSocketAddress = remoteSocketAddress;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final SessionKey that = (SessionKey) o;
      return Objects.equals(nodeId, that.nodeId)
          && Objects.equals(remoteSocketAddress, that.remoteSocketAddress);
    }

    @Override
    public int hashCode() {
      return Objects.hash(nodeId, remoteSocketAddress);
    }
  }
}
