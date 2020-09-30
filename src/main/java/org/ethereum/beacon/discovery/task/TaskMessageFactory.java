/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.task;

import java.util.Collections;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.AuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.pipeline.info.FindNodeRequestInfo;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.type.Bytes12;

public class TaskMessageFactory {
  public static OrdinaryMessagePacket createPacketFromRequest(
      RequestInfo requestInfo, Bytes12 authTag, NodeSession session) {
    switch (requestInfo.getTaskType()) {
      case PING:
        {
          return createPingPacket(authTag, session, requestInfo.getRequestId());
        }
      case FINDNODE:
        {
          FindNodeRequestInfo nodeRequestInfo = (FindNodeRequestInfo) requestInfo;
          return createFindNodePacket(
              authTag, session, requestInfo.getRequestId(), nodeRequestInfo.getDistance());
        }
      default:
        {
          throw new RuntimeException(
              String.format("Type %s is not supported!", requestInfo.getTaskType()));
        }
    }
  }

  public static V5Message createMessageFromRequest(RequestInfo requestInfo, NodeSession session) {
    switch (requestInfo.getTaskType()) {
      case PING:
        {
          return createPing(session, requestInfo.getRequestId());
        }
      case FINDNODE:
        {
          FindNodeRequestInfo nodeRequestInfo = (FindNodeRequestInfo) requestInfo;
          return createFindNode(requestInfo.getRequestId(), nodeRequestInfo.getDistance());
        }
      default:
        {
          throw new RuntimeException(
              String.format("Type %s is not supported!", requestInfo.getTaskType()));
        }
    }
  }

  public static OrdinaryMessagePacket createPingPacket(
      Bytes12 authTag, NodeSession session, Bytes requestId) {

    PingMessage pingMessage = createPing(session, requestId);
    Header<AuthData> header = Header.createOrdinaryHeader(session.getHomeNodeId(), authTag);
    return OrdinaryMessagePacket.create(header, pingMessage, session.getInitiatorKey());
  }

  public static PingMessage createPing(NodeSession session, Bytes requestId) {
    return new PingMessage(requestId, session.getNodeRecord().orElseThrow().getSeq());
  }

  public static OrdinaryMessagePacket createFindNodePacket(
      Bytes12 authTag, NodeSession session, Bytes requestId, int distance) {
    FindNodeMessage findNodeMessage = createFindNode(requestId, distance);
    Header<AuthData> header = Header.createOrdinaryHeader(session.getHomeNodeId(), authTag);
    return OrdinaryMessagePacket.create(header, findNodeMessage, session.getInitiatorKey());
  }

  public static FindNodeMessage createFindNode(Bytes requestId, int distance) {
    return new FindNodeMessage(requestId, Collections.singletonList(distance));
  }
}