/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/

package org.apache.edgent.connectors.iot;

import org.apache.edgent.function.Function;
import org.apache.edgent.function.UnaryOperator;
import org.apache.edgent.topology.TSink;
import org.apache.edgent.topology.TStream;
import org.apache.edgent.topology.TopologyElement;

import com.google.gson.JsonObject;

/**
 * Generic Internet of Things device connector.
 */
public interface IotDevice extends TopologyElement {
    
    /**
     * Device event and command identifiers starting with {@value} are reserved for use by Edgent.
     */
    String RESERVED_ID_PREFIX = "edgent";

    /**
     * Publish a stream's tuples as device events.
     * <p>
     * Each tuple is published as a device event with the supplied functions
     * providing the event identifier, payload and QoS. The event identifier and
     * QoS can be generated based upon the tuple.
     * 
     * @param stream
     *            Stream to be published.
     * @param eventId
     *            function to supply the event identifier.
     * @param payload
     *            function to supply the event's payload.
     * @param qos
     *            function to supply the event's delivery Quality of Service.
     * @return TSink sink element representing termination of this stream.
     */
    TSink<JsonObject> events(TStream<JsonObject> stream, Function<JsonObject, String> eventId,
            UnaryOperator<JsonObject> payload,
            Function<JsonObject, Integer> qos) ;
    
    /**
     * Publish a stream's tuples as device events.
     * <p>
     * Each tuple is published as a device event with fixed event identifier and
     * QoS.
     * 
     * @param stream
     *            Stream to be published.
     * @param eventId
     *            Event identifier.
     * @param qos
     *            Event's delivery Quality of Service.
     * @return TSink sink element representing termination of this stream.
     */
    TSink<JsonObject> events(TStream<JsonObject> stream, String eventId, int qos) ;
    
    /**
     * Command identifier key.
     * Key is {@value}.
     * 
     * @see #commands(String...)
     */
    String CMD_ID = "command";
    
    /**
     * Command timestamp (in milliseconds) key.
     * Key is {@value}.
     * 
     * @see #commands(String...)
     */
    String CMD_TS = "tsms";
    /**
     * Command format key.
     * Key is {@value}.
     * 
     * @see #commands(String...)
     */
    String CMD_FORMAT = "format";
    /**
     * Command payload key.
     * If the command format is {@code json} then
     * the key's value will be a {@code JsonObject},
     * otherwise a {@code String}.
     * Key is {@value}.
     * 
     * @see #commands(String...)
     */
    String CMD_PAYLOAD = "payload";

    /**
     * Create a stream of device commands as JSON objects.
     * Each command sent to the device matching {@code commands} will result in a tuple
     * on the stream. The JSON object has these keys:
     * <UL>
     * <LI>{@link #CMD_ID command} - Command identifier as a String</LI>
     * <LI>{@link #CMD_TS tsms} - Timestamp of the command in milliseconds since the 1970/1/1 epoch.</LI>
     * <LI>{@link #CMD_FORMAT format} - Format of the command as a String</LI>
     * <LI>{@link #CMD_PAYLOAD payload} - Payload of the command
     * <UL>
     * <LI>If {@code format} is {@code json} then {@code payload} is JSON</LI>
     * <LI>Otherwise {@code payload} is String</LI>
     * </UL>
     * </LI>
     * </UL>
     * 
     * 
     * @param commands Command identifiers to include. If no command identifiers are provided then the
     * stream will contain all device commands.
     * @return Stream containing device commands.
     */
    TStream<JsonObject> commands(String... commands);
}
