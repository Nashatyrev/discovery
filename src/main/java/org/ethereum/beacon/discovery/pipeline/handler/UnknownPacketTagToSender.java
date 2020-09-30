/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;

/**
 * Assuming we have some unknown packet in {@link Field#PACKET_UNKNOWN}, resolves sender node id
 * using `tag` field of the packet. Next, puts it to the {@link Field#SESSION_LOOKUP} so sender
 * session could be resolved by another handler.
 */
public class UnknownPacketTagToSender implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(UnknownPacketTagToSender.class);

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in UnknownPacketTagToSender, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.PACKET, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in UnknownPacketTagToSender, requirements are satisfied!",
                envelope.getId()));

    Packet<?> packet = (Packet<?>) envelope.get(Field.PACKET);
    envelope.put(
        Field.SESSION_LOOKUP,
        new SessionLookup(packet.getHeader().getStaticHeader().getSourceNodeId()));
  }
}