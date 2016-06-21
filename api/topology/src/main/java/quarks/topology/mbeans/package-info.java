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
 * Controls for executing topologies.
 * <h3>Application Service </h3>
 * {@linkplain quarks.topology.services.ApplicationService Application service}
 * allows an application to be registered
 * so that it be be submitted remotely using a device command.
 * 
 * <BR>
 * This service registers a control MBean
 * {@link quarks.topology.mbeans.ApplicationServiceMXBean}
 * to provide control of the service.
 *  
 * <h4>Submit an Application</h4>
 * Method: {@link quarks.topology.mbeans.ApplicationServiceMXBean#submit(String, String)}
 * <table border=1 cellpadding=3 cellspacing=1>
 * <caption>JSON Submit Application</caption>
 * <tr>
 *    <td align=center><b>Attribute name</b></td>
 *    <td align=center><b>Type</b></td>
 *    <td align=center><b>Value</b></td>
 *    <td align=center><b>Description</b></td>
 *  </tr>
 * <tr>
 *    <td>{@link quarks.runtime.jsoncontrol.JsonControlService#TYPE_KEY type}</td>
 *    <td>String</td>
 *    <td>{@link quarks.topology.mbeans.ApplicationServiceMXBean#TYPE appService}</td>
 *    <td>{@code ApplicationServiceMXBean} control MBean type.</td>
 *  </tr>
 *  <tr>
 *    <td>{@link quarks.runtime.jsoncontrol.JsonControlService#OP_KEY op}</td>
 *    <td>String</td>
 *    <td>{@code submit}</td>
 *    <td>Invoke {@link quarks.topology.mbeans.ApplicationServiceMXBean#submit(String, String) submit} operation
 *    against the control MBean.</td>
 *  </tr>
 *  <tr>
 *    <td>{@link quarks.runtime.jsoncontrol.JsonControlService#ALIAS_KEY alias}</td>
 *    <td>String</td>
 *    <td>Alias of control MBean.</td>
 *    <td>Default is {@link quarks.topology.services.ApplicationService#ALIAS quarks}.</td>
 *  </tr>
 *  <tr>
 *    <td rowspan="2">{@link quarks.runtime.jsoncontrol.JsonControlService#ARGS_KEY args}</td>
 *    <td rowspan="2">List</td>
 *    <td>String: application name</td>
 *    <td>Registered application to submit.</td>
 *  </tr>
 *  <tr>
 *    <td>JSON Object: submission configuration</td>
 *    <td>Configuration for the submission,
 *    see {@link quarks.execution.Submitter#submit(Object, com.google.gson.JsonObject) submit()}.
 *    If {@code jobName} is not set in the configuration then the job is submitted with {@code jobName} set to the
 *    application name.</td>
 *  </tr>
 * </table>
 * <BR>
 * Example submitting the application {@code EngineTemp} with no configuration, will result in a running
 * job named {@code EngineTemp}.
 * <BR>
 * {@code {"type":"appService","alias":"quarks","op":"submit","args":["EngineTemp",{}]}}
 */
package quarks.topology.mbeans;
