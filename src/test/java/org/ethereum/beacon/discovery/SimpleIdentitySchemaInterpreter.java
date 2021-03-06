/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;

public class SimpleIdentitySchemaInterpreter implements IdentitySchemaInterpreter {

  public static final String UDP_ADDRESS = "udpAddress";

  public static NodeRecord createNodeRecord(
      final Bytes nodeId, final InetSocketAddress udpAddress) {
    return new NodeRecordFactory(new SimpleIdentitySchemaInterpreter())
        .createFromValues(
            UInt64.ONE,
            new EnrField(EnrField.ID, IdentitySchema.V4),
            new EnrField(EnrField.PKEY_SECP256K1, nodeId),
            new EnrField(UDP_ADDRESS, udpAddress));
  }

  @Override
  public IdentitySchema getScheme() {
    return IdentitySchema.V4;
  }

  @Override
  public void sign(final NodeRecord nodeRecord, final Bytes privateKey) {
    nodeRecord.setSignature(MutableBytes.create(96));
  }

  @Override
  public Bytes getNodeId(final NodeRecord nodeRecord) {
    return (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1);
  }

  @Override
  public Optional<InetSocketAddress> getUdpAddress(final NodeRecord nodeRecord) {
    return Optional.ofNullable((InetSocketAddress) nodeRecord.get(UDP_ADDRESS));
  }

  @Override
  public Optional<InetSocketAddress> getTcpAddress(final NodeRecord nodeRecord) {
    return Optional.empty();
  }

  @Override
  public NodeRecord createWithNewAddress(
      final NodeRecord nodeRecord, final InetSocketAddress newAddress, final Bytes privateKey) {
    final NodeRecord newRecord = createNodeRecord(getNodeId(nodeRecord), newAddress);
    sign(newRecord, privateKey);
    return newRecord;
  }

  @Override
  public NodeRecord createWithUpdatedCustomField(
      final NodeRecord nodeRecord,
      final String fieldName,
      final Bytes value,
      final Bytes privateKey) {
    throw new UnsupportedOperationException(
        "SimpleIdentitySchemaInterpreter does not support updating custom fields.");
  }
}
