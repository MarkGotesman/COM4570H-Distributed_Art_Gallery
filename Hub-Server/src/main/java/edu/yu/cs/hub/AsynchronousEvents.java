package edu.yu.cs.hub;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.transaction.Transactional;


import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;

@Singleton
public class AsynchronousEvents {
    @Inject
    Utility utility;

    @Transactional
    public void init(@Observes StartupEvent se) {
        utility.setLeaderID();
        utility.updateServers();
    }
    
    @Scheduled(every="10s")
    public void healthCheck() {
        utility.galleryHealthCheck();
    }

}