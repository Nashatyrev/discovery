/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.TalkHandler;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.processor.DiscoveryV5MessageProcessor;
import org.ethereum.beacon.discovery.processor.MessageProcessor;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;

public class MessageHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(MessageHandler.class);
  private final MessageProcessor messageProcessor;

  public MessageHandler(
      NodeRecordFactory nodeRecordFactory,
      LocalNodeRecordStore localNodeRecordStore,
      TalkHandler talkHandler) {
    this.messageProcessor =
        new MessageProcessor(
            new DiscoveryV5MessageProcessor(nodeRecordFactory, localNodeRecordStore, talkHandler));
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in MessageHandler, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.MESSAGE, envelope)) {
      return;
    }
    if (!HandlerUtil.requireNodeRecord(envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in MessageHandler, requirements are satisfied!", envelope.getId()));

    NodeSession session = (NodeSession) envelope.get(Field.SESSION);
    V5Message message = (V5Message) envelope.get(Field.MESSAGE);
    try {
      messageProcessor.handleIncoming(DiscoveryV5Message.from(message), session);
    } catch (Exception ex) {
      logger.trace(
          () ->
              String.format(
                  "Failed to handle message %s in envelope #%s", message, envelope.getId()),
          ex);
      envelope.put(Field.BAD_MESSAGE, message);
      envelope.put(Field.BAD_EXCEPTION, ex);
      envelope.remove(Field.MESSAGE);
    }
  }
}
