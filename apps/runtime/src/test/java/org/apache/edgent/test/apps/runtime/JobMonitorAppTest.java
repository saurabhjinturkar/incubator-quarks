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
package org.apache.edgent.test.apps.runtime;

import static org.junit.Assert.assertTrue;

import java.util.Hashtable;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.edgent.apps.runtime.JobMonitorApp;
import org.apache.edgent.execution.DirectSubmitter;
import org.apache.edgent.execution.Job;
import org.apache.edgent.execution.services.ControlService;
import org.apache.edgent.execution.services.ServiceContainer;
import org.apache.edgent.providers.direct.DirectProvider;
import org.apache.edgent.runtime.appservice.AppService;
import org.apache.edgent.runtime.jmxcontrol.JMXControlService;
import org.apache.edgent.runtime.jobregistry.JobRegistry;
import org.apache.edgent.topology.TStream;
import org.apache.edgent.topology.Topology;
import org.apache.edgent.topology.services.ApplicationService;
import org.junit.Test;

public class JobMonitorAppTest {

    public final static String MONITORED_APP_NAME_1 = "MonitoredApp_1";
    public final static String MONITORED_APP_NAME_2 = "MonitoredApp_2";

    @Test
    public void testJobMonitorApp() throws Exception {
        DirectProvider provider = new DirectProvider();
        startProvider(provider);

        // Start monitor app
        JobMonitorApp app = new JobMonitorApp(provider, provider, JobMonitorApp.APP_NAME);
        Job monitor = app.submit();

        // Declare and register user apps which need monitoring
        registerMonitoredApplicationOne(provider);
        registerMonitoredApplicationTwo(provider);

        // Start monitored apps
        startMonitoredApplications(provider);
        
        // Run for a while, assert the monitor app is still running healthy
        Thread.sleep(5000);
        assertTrue(
                Job.State.RUNNING.equals(monitor.getCurrentState()) &&
                Job.State.RUNNING.equals(monitor.getNextState()) &&
                Job.Health.HEALTHY.equals(monitor.getHealth()));
    }

    static void startProvider(DirectProvider provider) 
            throws InterruptedException, ExecutionException {
        
        provider.getServices().addService(ControlService.class,
                new JMXControlService("org.apache.edgent.test.apps.runtime", new Hashtable<>()));
        AppService.createAndRegister(provider, provider);
        JobRegistry.createAndRegister(provider.getServices());        
    }

    /**
     * Fails every 2 seconds (20 tuples * 100 millis)
     */
    static void registerMonitoredApplicationOne(DirectSubmitter<Topology, Job> submitter) {
        ApplicationService appService = submitter.getServices().getService(ApplicationService.class);
        appService.registerTopology(MONITORED_APP_NAME_1, (topology, config) -> {
                
                Random r = new Random();
                TStream<Double> d  = topology.poll(() -> r.nextGaussian(), 100, TimeUnit.MILLISECONDS);
                
                final AtomicInteger count = new AtomicInteger(0);
                d = d.filter(tuple -> {
                    int tupleCount = count.incrementAndGet();
                    if (tupleCount == 20) {
                        throw new IllegalStateException("Injected error");
                    }
                    return true; 
                });
                
                d.sink(tuple -> System.out.print("."));
            });
    }

    /**
     * Fails every 1.5 seconds (10 tuples * 150 millis)
     */
    static void registerMonitoredApplicationTwo(DirectSubmitter<Topology, Job> submitter) {
        ApplicationService appService = submitter.getServices().getService(ApplicationService.class);
        appService.registerTopology(MONITORED_APP_NAME_2, (topology, config) -> {
                
                Random r = new Random();
                TStream<Double> d  = topology.poll(() -> r.nextGaussian(), 150, TimeUnit.MILLISECONDS);
                
                final AtomicInteger count = new AtomicInteger(0);
                d = d.filter(tuple -> {
                    int tupleCount = count.incrementAndGet();
                    if (tupleCount == 10) {
                        throw new IllegalStateException("Injected error");
                    }
                    return true; 
                });
                
                d.sink(tuple -> System.out.print("#"));
            });
    }

    static void startMonitoredApplications(DirectSubmitter<Topology, Job> submitter) {
        ServiceContainer services = submitter.getServices();
        ApplicationService appService = services.getService(ApplicationService.class);
        ControlService controlService = services.getService(ControlService.class);

        // Submit all applications registered with the ApplicationService
        for (String name: appService.getApplicationNames()) {
            JobMonitorApp.submitApplication(name, controlService);
        }
    }
}
