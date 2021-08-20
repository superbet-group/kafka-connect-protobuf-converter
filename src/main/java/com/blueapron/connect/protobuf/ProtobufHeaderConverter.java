package com.blueapron.connect.protobuf;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.storage.HeaderConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;


/**
 * Implementation of HeaderConverter that uses Protobufs.
 */
public class ProtobufHeaderConverter implements HeaderConverter {
  private static final Logger log = LoggerFactory.getLogger(ProtobufHeaderConverter.class);
  private static final String PROTO_CLASS_NAME_CONFIG = "protoClassName";
  private static final String LEGACY_NAME_CONFIG = "legacyName";
  private static final String PROTO_MAP_CONVERSION_TYPE = "protoMapConversionType";

  private static final ConfigDef CONFIG_DEF = new ConfigDef()
    .define(PROTO_CLASS_NAME_CONFIG, Type.STRING, Importance.HIGH, "Name of the class that will be used to do the conversion")
    .define(LEGACY_NAME_CONFIG, Type.STRING, "legacy_name", Importance.LOW, "In order to support these output formats, we use a custom field option to specify the original name and keep the Kafka Connect schema consistent")
    .define(PROTO_MAP_CONVERSION_TYPE, Type.STRING, "array", Importance.LOW, "Whether to convert the Protobuf map type to an Array of Struct or into Connect Schema Map type");

  private ProtobufData protobufData;

  @Override
  public ConfigDef config() {
    return CONFIG_DEF;
  }

  @Override
  public void configure(Map<String, ?> configs) {
    Object legacyName = configs.get(LEGACY_NAME_CONFIG);
    String legacyNameString = legacyName == null ? "legacy_name" : legacyName.toString();
    boolean useConnectSchemaMap = "map".equals(configs.get(PROTO_MAP_CONVERSION_TYPE));

    Object protoClassName = configs.get(PROTO_CLASS_NAME_CONFIG);

    if (protoClassName == null) {
      protobufData = null;
      return;
    }

    String protoClassNameString = protoClassName.toString();
    try {
      log.info("Initializing Header ProtobufData with args: [protoClassName={}, legacyName={}, useConnectSchemaMap={}]", protoClassNameString, legacyNameString, useConnectSchemaMap);
      protobufData = new ProtobufData(Class.forName(protoClassNameString).asSubclass(com.google.protobuf.GeneratedMessageV3.class), legacyNameString, useConnectSchemaMap);
    } catch (ClassNotFoundException e) {
      throw new ConnectException("Proto class " + protoClassNameString + " not found in the classpath");
    } catch (ClassCastException e) {
      throw new ConnectException("Proto class " + protoClassNameString + " is not a valid proto3 message class");
    }
  }

  @Override
  public byte[] fromConnectHeader(String topic, String headerKey, Schema schema, Object value) {
    if (protobufData == null || schema == null || value == null) {
      return null;
    }

    return protobufData.fromConnectData(value);
  }

  @Override
  public SchemaAndValue toConnectHeader(String topic, String headerKey, byte[] value) {
    if (protobufData == null || value == null) {
      return SchemaAndValue.NULL;
    }

    return protobufData.toConnectData(value);
  }

  @Override
  public void close() throws IOException {
    // do nothing
  }
}
