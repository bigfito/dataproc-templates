/*
 * Copyright (C) 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.dataproc.templates.kafka;

import static com.google.cloud.dataproc.templates.util.TemplateConstants.*;

import com.google.cloud.dataproc.templates.BaseTemplate;
import com.google.cloud.spark.bigquery.repackaged.com.google.cloud.bigquery.*;
import java.io.Serializable;
import java.util.*;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.*;
import org.apache.spark.api.java.function.*;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.streaming.Seconds;
import org.apache.spark.streaming.api.java.*;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaToPubSub implements BaseTemplate, Serializable {
  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaToPubSub.class);
  private String kafkaBootstrapServers;
  private String kafkaTopic;
  private String pubsubCheckpointLocation;
  private String kafkaStartingOffsets;
  private Long kafkaAwaitTerminationTimeout;

  public KafkaToPubSub() {
    kafkaBootstrapServers = getProperties().getProperty(KAFKA_PUBSUB_BOOTSTRAP_SERVERS);
    kafkaTopic = getProperties().getProperty(KAFKA_PUBSUB_TOPIC);
    pubsubCheckpointLocation = getProperties().getProperty(KAFKA_PUBSUB_CHECKPOINT_LOCATION);
    kafkaStartingOffsets = getProperties().getProperty(KAFKA_PUBSUB_STARTING_OFFSET);
    kafkaAwaitTerminationTimeout =
        Long.valueOf(getProperties().getProperty(KAFKA_PUBSUB_AWAIT_TERMINATION_TIMEOUT));
  }

  @Override
  public void runTemplate() {
    if (StringUtils.isAllBlank(pubsubCheckpointLocation)
        || StringUtils.isAllBlank(kafkaBootstrapServers)
        || StringUtils.isAllBlank(kafkaTopic)) {
      LOGGER.error(
          "{},{},{} is required parameter. ",
          KAFKA_PUBSUB_CHECKPOINT_LOCATION,
          KAFKA_PUBSUB_BOOTSTRAP_SERVERS,
          KAFKA_PUBSUB_TOPIC);
      throw new IllegalArgumentException(
          "Required parameters for KafkaToPubSub not passed. "
              + "Set mandatory parameter for KafkaToPubSub template "
              + "in resources/conf/template.properties file.");
    }

    SparkSession spark = null;
    SparkConf sparkConf = null;
    JavaStreamingContext streamingContext = null;
    LOGGER.info(
        "Starting Kafka to PubSub spark job with following parameters:"
            + "1. {}:{}"
            + "2. {}:{}"
            + "3. {}:{}"
            + "4. {},{}"
            + "5, {},{}",
        KAFKA_PUBSUB_CHECKPOINT_LOCATION,
        pubsubCheckpointLocation,
        KAFKA_PUBSUB_BOOTSTRAP_SERVERS,
        kafkaBootstrapServers,
        KAFKA_PUBSUB_TOPIC,
        kafkaTopic,
        KAFKA_PUBSUB_STARTING_OFFSET,
        kafkaStartingOffsets,
        KAFKA_PUBSUB_AWAIT_TERMINATION_TIMEOUT,
        kafkaAwaitTerminationTimeout);

    try {
      // Initialize the Spark session
      // spark = SparkSession.builder().appName("Spark KafkaToPubSub Job").getOrCreate();
      sparkConf = new SparkConf().setAppName("Spark KafkaToPubSub Job");
      streamingContext = new JavaStreamingContext(sparkConf, Seconds.apply(15));

      Map<String, Object> kafkaParams = new HashMap<>();
      kafkaParams.put("bootstrap.servers", "10.0.0.20:9092");
      kafkaParams.put("key.deserializer", StringDeserializer.class);
      kafkaParams.put("value.deserializer", StringDeserializer.class);
      // kafkaParams.put("group.id", "test-consumer-group"); // kafkaParams.put("group.id",
      // "use_a_separate_group_id_for_each_stream");
      kafkaParams.put("auto.offset.reset", "earliest");
      kafkaParams.put("enable.auto.commit", false);

      Collection<String> topics = Arrays.asList("test");

      JavaInputDStream<ConsumerRecord<String, String>> stream =
          KafkaUtils.createDirectStream(
              streamingContext,
              LocationStrategies.PreferConsistent(),
              ConsumerStrategies.<String, String>Subscribe(topics, kafkaParams));

      stream.foreachRDD(
          rdd -> {
            rdd.foreachPartition(
                consumerRecords -> {
                  System.out.println("consumerRecords: " + consumerRecords);
                });
          });

      streamingContext.start();
      streamingContext.awaitTerminationOrTimeout(1200);
      LOGGER.info("KakfaToPubSub job completed.");
      streamingContext.stop();
    } catch (Throwable th) {
      LOGGER.error("Exception in KakfaToPubSub", th);
      if (Objects.nonNull(spark)) {
        streamingContext.stop();
      }
    }
  }
}
