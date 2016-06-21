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
package quarks.providers.iot;

import static quarks.topology.services.ApplicationService.SYSTEM_APP_PREFIX;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import com.google.gson.JsonObject;

import quarks.apps.iot.IotDevicePubSub;
import quarks.connectors.iot.Commands;
import quarks.connectors.iot.IotDevice;
import quarks.connectors.pubsub.service.ProviderPubSub;
import quarks.connectors.pubsub.service.PublishSubscribeService;
import quarks.execution.DirectSubmitter;
import quarks.execution.Job;
import quarks.execution.services.ControlService;
import quarks.execution.services.ServiceContainer;
import quarks.function.BiConsumer;
import quarks.function.Function;
import quarks.providers.direct.DirectProvider;
import quarks.runtime.appservice.AppService;
import quarks.runtime.jsoncontrol.JsonControlService;
import quarks.topology.TStream;
import quarks.topology.Topology;
import quarks.topology.TopologyProvider;
import quarks.topology.mbeans.ApplicationServiceMXBean;
import quarks.topology.services.ApplicationService;

/**
 * IoT provider supporting multiple topologies with a single connection to a
 * message hub. A provider that uses a single {@link IotDevice} to communicate
 * with an IoT scale message hub.
 * {@link quarks.connectors.pubsub.PublishSubscribe Publish-subscribe} is
 * used to allow multiple topologies to communicate through the single
 * connection.
 * <P>
 * This provider registers these services:
 * <UL>
 * <LI>{@link ControlService control} - An instance of {@link JsonControlService}.</LI>
 * <LI>{@link ApplicationService application} - An instance of {@link AppService}.</LI>
 * <LI>{@link PublishSubscribeService publish-subscribe} - An instance of {@link ProviderPubSub}</LI>
 * </UL>
 * System applications provide this functionality:
 * <UL>
 * <LI>Single connection to the message hub using an {@code IotDevice}
 * using {@link IotDevicePubSub}.
 * Applications using this provider that want to connect
 * to the message hub for device events and commands must create an instance of
 * {@code IotDevice} using {@link IotDevicePubSub#addIotDevice(quarks.topology.TopologyElement)}</LI>
 * <LI>Access to the control service through device commands from the message hub using command
 * identifier {@link Commands#CONTROL_SERVICE quarksControl}.
 * </UL>
 * <P>
 * An {@code IotProvider} is created with a provider and submitter that it delegates
 * the creation and submission of topologies to.
 * </P>
 * 
 * @see IotDevice
 * @see IotDevicePubSub
 */
public class IotProvider implements TopologyProvider,
 DirectSubmitter<Topology, Job> {
    
    /**
     * IoT control using device commands application name.
     */
    public static final String CONTROL_APP_NAME = SYSTEM_APP_PREFIX + "IotCommandsToControl";
    
    private final TopologyProvider provider;
    private final Function<Topology, IotDevice> iotDeviceCreator;
    private final DirectSubmitter<Topology, Job> submitter;
    
    /**
     * System applications by name.
     */
    private final List<String> systemApps = new ArrayList<>();

    private JsonControlService controlService = new JsonControlService();
    
    /**
     * Create an {@code IotProvider} that uses its own {@code DirectProvider}.
     * @param iotDeviceCreator How the {@code IotDevice} is created.
     * 
     * @see DirectProvider
     */
    public IotProvider(Function<Topology, IotDevice> iotDeviceCreator) {   
        this(new DirectProvider(), iotDeviceCreator);
    }
    
    /**
     * Create an {@code IotProvider} that uses the passed in {@code DirectProvider}.
     * 
     * @param provider {@code DirectProvider} to use for topology creation and submission.
     * @param iotDeviceCreator How the {@code IotDevice} is created.
     * 
     * @see DirectProvider
     *
     */
    public IotProvider(DirectProvider provider, Function<Topology, IotDevice> iotDeviceCreator) {
        this(provider, provider, iotDeviceCreator);
    }

    /**
     * Create an {@code IotProvider}.
     * @param provider How topologies are created.
     * @param submitter How topologies will be submitted.
     * @param iotDeviceCreator How the {@code IotDevice} is created.
     * 
     */
    public IotProvider(TopologyProvider provider, DirectSubmitter<Topology, Job> submitter,
            Function<Topology, IotDevice> iotDeviceCreator) {
        this.provider = provider;
        this.submitter = submitter;
        this.iotDeviceCreator = iotDeviceCreator;
        
        registerControlService();
        registerApplicationService();
        registerPublishSubscribeService();
        
        createIotDeviceApp();
        createIotCommandToControlApp();
    }
    
    /**
     * Get the application service.
     * Callers may use this to register applications to
     * be executed by this provider.
     * @return application service.
     */
    public ApplicationService getApplicationService() {
        return getServices().getService(ApplicationService.class);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceContainer getServices() {
        return submitter.getServices();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public final Topology newTopology() {
        return provider.newTopology();
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public final Topology newTopology(String name) {
        return provider.newTopology(name);
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<Job> submit(Topology topology) {
        return submitter.submit(topology);
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<Job> submit(Topology topology, JsonObject config) {
        return submitter.submit(topology, config);
    }

    protected void registerControlService() {
        getServices().addService(ControlService.class, getControlService());
    }

    protected void registerApplicationService() {
        AppService.createAndRegister(this, this);
    }
    protected void registerPublishSubscribeService() {
        getServices().addService(PublishSubscribeService.class, 
                new ProviderPubSub());
    }

    protected JsonControlService getControlService() {
        return controlService;
    }
    
    /**
     * Create application that connects to the message hub.
     * Subscribes to device events and sends them to the messages hub.
     * Publishes device commands from the message hub.
     * @see IotDevicePubSub
     * @see #createMessageHubDevice(Topology)
     */
    protected void createIotDeviceApp() {
        
        getApplicationService().registerTopology(IotDevicePubSub.APP_NAME,
                (topology, config) -> IotDevicePubSub.createApplication(createMessageHubDevice(topology)));

        systemApps.add(IotDevicePubSub.APP_NAME);
    }
    
    /**
     * Create application connects {@code quarksControl} device commands
     * to the control service.
     * 
     * Subscribes to device
     * commands of type {@link Commands#CONTROL_SERVICE}
     * and sends the payload into the JSON control service
     * to invoke the control operation.
     */
    protected void createIotCommandToControlApp() {
         
        this.registerTopology(CONTROL_APP_NAME, (iotDevice, config) -> {
            TStream<JsonObject> controlCommands = iotDevice.commands(Commands.CONTROL_SERVICE);
            controlCommands.sink(cmd -> {
                try {
                    getControlService().controlRequest(cmd.getAsJsonObject(IotDevice.CMD_PAYLOAD));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        });

        systemApps.add(CONTROL_APP_NAME);
    }
    
    /**
     * Start this provider by starting its system applications.
     * 
     * @throws Exception on failure starting applications.
     */
    public void start() throws Exception {
        ApplicationServiceMXBean bean = getControlService().getControl(ApplicationServiceMXBean.TYPE,
                ApplicationService.ALIAS, ApplicationServiceMXBean.class);
        
        for (String systemAppName : systemApps) {
            bean.submit(systemAppName, null /* no config */);
        }
    }

    /**
     * Create the connection to the message hub.
     * 
     * Creates an instance of {@link IotDevice}
     * used to communicate with the message hub. This
     * provider creates and submits an application
     * that subscribes to published events to send
     * as device events and publishes device commands.
     * <BR>
     * The application is created using
     * {@link IotDevicePubSub#createApplication(IotDevice)}.
     * <BR>
     * The {@code IotDevice} is created using the function
     * passed into the constructor.
     * 
     * @param topology Topology the {@code IotDevice} will be contained in.
     * @return IotDevice device used to communicate with the message hub.
     * 
     * @see IotDevice
     * @see IotDevicePubSub
     */
    protected IotDevice createMessageHubDevice(Topology topology) {
        return iotDeviceCreator.apply(topology);
    }
    
    /**
     * Register an application that uses an {@code IotDevice}.
     * <BR>
     * Wrapper around {@link ApplicationService#registerTopology(String, BiConsumer)}
     * that passes in an {@link IotDevice} and configuration to the supplied
     * function {@code builder} that builds the application. The passed
     * in {@code IotDevice} is created using {@link IotDevicePubSub#addIotDevice(quarks.topology.TopologyElement)}.
     * <BR>
     * Note that {@code builder} obtains a reference to its topology using
     * {@link IotDevice#topology()}.
     * <P>
     * When the application is
     * {@link quarks.topology.mbeans.ApplicationServiceMXBean#submit(String, String) submitted} {@code builder.accept(iotDevice, config)}
     * is called to build the application's graph.
     * </P>
     * 
     * @param applicationName Application name
     * @param builder Function that builds the topology.
     */
    public void registerTopology(String applicationName, BiConsumer<IotDevice, JsonObject> builder) {
        getApplicationService().registerTopology(applicationName,
                (topology,config) -> builder.accept(IotDevicePubSub.addIotDevice(topology), config));
    }
}
