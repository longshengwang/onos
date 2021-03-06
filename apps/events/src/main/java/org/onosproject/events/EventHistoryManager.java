/*
 * Copyright 2016-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.events;

import org.onlab.util.UnmodifiableDeque;
import org.onosproject.cluster.ClusterService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.Event;
import org.onosproject.event.ListenerTracker;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.onlab.util.Tools.minPriority;
import static org.onosproject.events.OsgiPropertyConstants.EXCLUDE_STATS_EVENT;
import static org.onosproject.events.OsgiPropertyConstants.EXCLUDE_STATS_EVENT_DEFAULT;
import static org.onosproject.events.OsgiPropertyConstants.SIZE_LIMIT;
import static org.onosproject.events.OsgiPropertyConstants.SIZE_LIMIT_DEFAULT;

/**
 * Application to store history of instance local ONOS Events.
 */
@Component(
    immediate = true,
    service = EventHistoryService.class,
    property = {
        EXCLUDE_STATS_EVENT + "=" + EXCLUDE_STATS_EVENT_DEFAULT,
        SIZE_LIMIT + "=" + SIZE_LIMIT_DEFAULT
    }
)
public class EventHistoryManager
    implements EventHistoryService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigService netcfgService;

    /** Exclude stats related events. */
    private boolean excludeStatsEvent = EXCLUDE_STATS_EVENT_DEFAULT;

    /** Number of event history to store. */
    private int sizeLimit = SIZE_LIMIT_DEFAULT;

    private ApplicationId appId;

    private ListenerTracker listeners;

    // Using Deque so that it'll be possible to iterate from both ends
    // (Tail-end is the most recent event)
    private final Deque<Event<?, ?>> history = new ConcurrentLinkedDeque<>();

    private ScheduledExecutorService pruner;

    // pruneEventHistoryTask() execution interval in seconds
    private long pruneInterval = 5;


    @Activate
    protected void activate() {
        appId = coreService.registerApplication("org.onosproject.events");
        log.debug("Registered as {}", appId);

        pruner = newSingleThreadScheduledExecutor(
                  minPriority(groupedThreads("onos/events", "history-pruner", log)));

        pruner.scheduleWithFixedDelay(this::pruneEventHistoryTask,
                                      pruneInterval, pruneInterval, TimeUnit.SECONDS);

        listeners = new ListenerTracker();
        listeners.addListener(mastershipService, this::addEvent)
                 .addListener(deviceService, new InternalDeviceListener())
                 .addListener(linkService, this::addEvent)
                 .addListener(topologyService, this::addEvent)
                 .addListener(hostService, this::addEvent)
                 .addListener(clusterService, this::addEvent)
                 .addListener(edgeService, this::addEvent)
                 .addListener(intentService, this::addEvent)
                 .addListener(netcfgService, this::addEvent);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        listeners.removeListeners();

        pruner.shutdownNow();
        history.clear();

        log.info("Stopped");
    }

    @Override
    public Deque<Event<?, ?>> history() {
        return UnmodifiableDeque.unmodifiableDeque(history);
    }

    @Override
    public void clear() {
        history.clear();
    }

    // This method assumes only 1 call is in flight at the same time.
    private void pruneEventHistoryTask() {
        int size = history.size();
        int overflows = size - sizeLimit;
        if (overflows > 0) {
            for (int i = 0; i < overflows; ++i) {
                history.poll();
            }
        }
    }

    private void addEvent(Event<?, ?> event) {
        if (log.isTraceEnabled()) {
            log.trace(event.toString());
        }
        history.offer(event);
    }

    class InternalDeviceListener
            implements DeviceListener {

        @Override
        public boolean isRelevant(DeviceEvent event) {
            if (excludeStatsEvent) {
                return event.type() != DeviceEvent.Type.PORT_STATS_UPDATED;
            } else {
                return true;
            }
        }

        @Override
        public void event(DeviceEvent event) {
            addEvent(event);
        }
    }

}
