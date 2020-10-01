/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.MessageCode;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.message.TalkReqMessage;
import org.ethereum.beacon.discovery.message.TalkRespMessage;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.impl.MessagePacketImpl;
import org.ethereum.beacon.discovery.packet.impl.OrdinaryMessageImpl;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.util.DecryptException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;

public class OrdinaryMessagePacketTest {

  private final Bytes32 srcNodeId =
      Bytes32.fromHexString("0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb");
  private final Bytes32 destNodeId =
      Bytes32.fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
  private final Bytes16 headerMaskingKey = Bytes16.wrap(destNodeId, 0);
  private final Bytes12 aesGcmNonce = Bytes12.fromHexString("0xffffffffffffffffffffffff");
  private final Bytes secretKey = Bytes.fromHexString("0x00000000000000000000000000000000");
  private final Bytes16 aesCtrIV = Bytes16.fromHexString("0x00000000000000000000000000000000");

  static Stream<Arguments> testMessages() {
    return Stream.of(
        Arguments.of(
            new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(2)),
            "Ping-1"),
        Arguments.of(
            new PingMessage(Bytes.fromHexString("0xFF00000000000001"), UInt64.MAX_VALUE),
            "Ping-2"),
        Arguments.of(
            new PongMessage(
                Bytes.fromHexString("0x00000001"),
                UInt64.valueOf(2),
                Bytes.fromHexString("0x01020304"),
                777),
            "Pong-1"),
        Arguments.of(
            new PongMessage(
                Bytes.fromHexString("0xFF00000000000001"),
                UInt64.MAX_VALUE,
                Bytes.fromHexString("0xFFFFFFFF"),
                65535),
            "Pong-2"),
        Arguments.of(
            new FindNodeMessage(Bytes.fromHexString("0x00000001"), List.of(255)),
            "FindNode-1"),
        Arguments.of(
            new FindNodeMessage(Bytes.fromHexString("0x00000001"), List.of(255, 254, 253)),
            "FindNode-2"),
        Arguments.of(
            new NodesMessage(Bytes.fromHexString("0x00000001"), 0, emptyList()),
            "Nodes-1"),
        Arguments.of(
            new NodesMessage(
                Bytes.fromHexString("0x00000001"),
                10000000,
                List.of(
                    NodeRecord.fromValues(
                        IdentitySchemaInterpreter.V4,
                        UInt64.MAX_VALUE,
                        List.of(
                            new EnrField("aaaaa1", Bytes.fromHexString("0xba0bab")),
                            new EnrField("aaaaa2", Bytes.fromHexString("0xb100da")))))),
            "Nodes-2"),
        Arguments.of(
            new TalkReqMessage(
                Bytes.fromHexString("0x00000001"), "proto1", Bytes.fromHexString("0x11223344")),
            "TalkReq-1"),
        Arguments.of(
            new TalkRespMessage(
                Bytes.fromHexString("0x00000001"), Bytes.fromHexString("0x11223344")),
            "TalkResp-1"));
  }

  @ParameterizedTest(name = "{index} {2}")
  @MethodSource("testMessages")
  void testMessageRoundtrip(V5Message message, String name) {
    RawPacket rawPacket = createPacket(message);
    Bytes packetBytes = rawPacket.getBytes();

    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    OrdinaryMessagePacket packet =
        (OrdinaryMessagePacket) rawPacket1.decodePacket(headerMaskingKey);
    V5Message message1 = packet.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
    assertThat(message1).isEqualTo(message);
  }

  @ParameterizedTest(name = "{index} {2}")
  @MethodSource("testMessages")
  void testDecryptingWithWrongKeyFails(V5Message message, String name) {
    Bytes packetBytes = createPacket(message).getBytes();
    MessagePacket<?> msgPacket =
        (MessagePacket<?>) RawPacket.decode(packetBytes).decodePacket(headerMaskingKey);
    Bytes wrongSecretKey = Bytes.fromHexString("0x00000000000000000000000000000001");
    assertThatThrownBy(
            () -> {
              msgPacket.decryptMessage(wrongSecretKey, NodeRecordFactory.DEFAULT);
            })
        .isInstanceOf(DecryptException.class);
  }

  @ParameterizedTest(name = "{index} {2}")
  @MethodSource("testMessages")
  void testDecryptingWithWrongGcmNonceFails(V5Message message, String name) {
    Header<AuthData> header = Header.createOrdinaryHeader(srcNodeId, aesGcmNonce);
    Bytes12 wrongAesGcmNonce = Bytes12.fromHexString("0xafffffffffffffffffffffff");
    Bytes encryptedMessage =
        MessagePacketImpl.encrypt(
            header.getBytes(), message.getBytes(), wrongAesGcmNonce, secretKey);

    OrdinaryMessagePacket messagePacket = new OrdinaryMessageImpl(header, encryptedMessage);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    MessagePacket<?> msgPacket =
        (MessagePacket<?>) RawPacket.decode(packetBytes).decodePacket(headerMaskingKey);
    assertThatThrownBy(
            () -> {
              msgPacket.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
            })
        .isInstanceOf(DecryptException.class);
  }

  @Test
  void testDecryptingWithInvalidMessageCodeFails() {
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(2));
    Header<AuthData> header = Header.createOrdinaryHeader(srcNodeId, aesGcmNonce);
    Bytes invalidMessageBytes = Bytes.wrap(Bytes.of(111), pingMessage.getBytes().slice(1));
    Bytes encryptedInvalidMessage =
        MessagePacketImpl.encrypt(header.getBytes(), invalidMessageBytes, aesGcmNonce, secretKey);

    OrdinaryMessagePacket messagePacket = new OrdinaryMessageImpl(header, encryptedInvalidMessage);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    MessagePacket<?> msgPacket =
        (MessagePacket<?>) RawPacket.decode(packetBytes).decodePacket(headerMaskingKey);
    assertThatThrownBy(
            () -> {
              msgPacket.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
            })
        .isInstanceOf(DecodeException.class);
  }

  @Test
  void testDecryptingRandomMessageFails() {
    Header<AuthData> header = Header.createOrdinaryHeader(srcNodeId, aesGcmNonce);

    OrdinaryMessagePacket messagePacket = OrdinaryMessagePacket.createRandom(header, 111);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    MessagePacket<?> msgPacket =
        (MessagePacket<?>) RawPacket.decode(packetBytes).decodePacket(headerMaskingKey);
    assertThatThrownBy(
            () -> {
              msgPacket.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
            })
        .isInstanceOf(DecodeException.class);
  }

  @ParameterizedTest(name = "{index} {2}")
  @MethodSource("testMessages")
  void testDecryptingWithInvalidMessageRlpFails(V5Message message, String name) {
    Header<AuthData> header = Header.createOrdinaryHeader(srcNodeId, aesGcmNonce);
    Bytes messageBytes = message.getBytes();
    Bytes invalidMessageBytes = messageBytes.slice(0, messageBytes.size() - 1);
    Bytes encryptedInvalidMessage =
        MessagePacketImpl.encrypt(header.getBytes(), invalidMessageBytes, aesGcmNonce, secretKey);

    OrdinaryMessagePacket messagePacket = new OrdinaryMessageImpl(header, encryptedInvalidMessage);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    MessagePacket<?> msgPacket =
        (MessagePacket<?>) RawPacket.decode(packetBytes).decodePacket(headerMaskingKey);
    assertThatThrownBy(
            () -> {
              msgPacket.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
            })
        .isInstanceOf(DecodeException.class);
  }

  @Test
  void testDecryptingWithInvalidMessageRequestIdRlpFails() {
    Header<AuthData> header = Header.createOrdinaryHeader(srcNodeId, aesGcmNonce);
    RlpList rlpList =
        new RlpList(
            new RlpList(), // should be RlpString with requestId
            RlpString.create(1));
    Bytes invalidMessageBytes =
        Bytes.concatenate(
            Bytes.of(MessageCode.PING.byteCode()), Bytes.wrap(RlpEncoder.encode(rlpList)));

    Bytes encryptedInvalidMessage =
        MessagePacketImpl.encrypt(header.getBytes(), invalidMessageBytes, aesGcmNonce, secretKey);

    OrdinaryMessagePacket messagePacket = new OrdinaryMessageImpl(header, encryptedInvalidMessage);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    MessagePacket<?> msgPacket =
        (MessagePacket<?>) RawPacket.decode(packetBytes).decodePacket(headerMaskingKey);
    assertThatThrownBy(
            () -> {
              msgPacket.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
            })
        .isInstanceOf(DecodeException.class);
  }

  private RawPacket createPacket(V5Message msg) {
    Header<AuthData> header = Header.createOrdinaryHeader(srcNodeId, aesGcmNonce);
    OrdinaryMessagePacket messagePacket = OrdinaryMessagePacket.create(header, msg, secretKey);
    return RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
  }
}
