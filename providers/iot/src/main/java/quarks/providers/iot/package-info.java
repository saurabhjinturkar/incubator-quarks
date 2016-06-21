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

/**
 * Iot provider that allows multiple applications to
 * share an {@code IotDevice}.
 * 
 * <H3>IoT device</H3>
 * 
 * <H3>Application registration</H3>
 * The provider includes an {@link quarks.topology.services.ApplicationService ApplicationService} that allows applications
 * to be registered by name. Once registered an application can be started (and stopped) remotely
 * through the control service using a device command.
 * 
 * <H3>Supported device commands</H3>
 * This provider supports a number of system level device commands to control the applications
 * running within it.
 * <H4>Control service</H4>
 * Device commands with the command identifier '{@link quarks.connectors.iot.Commands#CONTROL_SERVICE quarksControl}'
 * are sent to the provider's control service, an instance of {@link quarks.runtime.jsoncontrol.JsonControlService JsonControlService}.
 * This allows invocation of an operation against a control mbean registered with the
 * control service, either by an application or the provider itself.
 * <BR>
 * The command's data (JSON) uniquely identifies a control MBean through its type and
 * alias, and indicates the operation to call on the MBean and the arguments to
 * pass the control MBean.
 * <BR>
 * Thus any control operation can be remotely invoked through a {@code quarksControl} device command,
 * including arbitrary control mbeans registered by applications.
 * 
 * <H4>Provider operations</H4>
 * <TABLE border="1">
 * <caption>Device Commands</caption>
 * <tr>
 *     <th>Operation</th><th>Command identifier</th>
 *     <th>Type</th><th>Alias</th><th>Method</th><th>Arguments</th>
 *     <th>Control MBean</th>
 * </tr>
 * <tr>
 *    <th rowspan="2">Submit (start) a registered application</th>
 *    <td>{@code quarksControl}</td><td>{@code appService}</td>
 *    <td>{@code quarks}</td><td>{@link quarks.topology.mbeans.ApplicationServiceMXBean#submit(String, String) submit}</td>
 *    <td><em>{@code [applicationName, config]}</em></td>
 *    <td>{@link quarks.topology.mbeans.ApplicationServiceMXBean ApplicationServiceMXBean}</td>
 * </tr>
 * <tr>
 * <th>Sample command data</th>
 * <td colspan=5>{@code {"type":"appService","alias":"quarks","op":"submit","args":["Heartbeat",{}]}}</td>
 * </tr>
 * <tr></tr>
 * 
 * <tr>
 *    <th rowspan="2">Close (stop) a running registered application</th>
 *    <td>{@code quarksControl}</td><td>{@link quarks.execution.mbeans.JobMXBean#TYPE job}</td>
 *    <td><em>{@code applicationName}</em></td><td>{@link quarks.execution.mbeans.JobMXBean#stateChange(quarks.execution.Job.Action) stateChange}</td>
 *    <td>{@code ["CLOSE"]}</td>
 *    <td>{@link quarks.execution.mbeans.JobMXBean JobMXBean}</td>
 * </tr>
 * <tr>
 * <th>Sample command data</th>
 * <td colspan=5>{@code {"type":"job","alias":"Heartbeat","op":"stateChange","args":["CLOSE"]}}</td>
 * </tr>
 * <tr></tr>
 * </TABLE>
 * 
 */
package quarks.providers.iot;
