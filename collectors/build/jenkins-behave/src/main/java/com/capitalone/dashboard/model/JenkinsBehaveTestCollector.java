package com.capitalone.dashboard.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension of Collector that stores current build server configuration.
 */
public class JenkinsBehaveTestCollector extends Collector {
    private List<String> buildServers = new ArrayList<>();
    
    public List<String> getBuildServers() {
        return buildServers;
    }

    public void setBuildServers(List<String> buildServers) {
        this.buildServers = buildServers;
    }

    public static JenkinsBehaveTestCollector prototype(List<String> buildServers) {
        JenkinsBehaveTestCollector protoType = new JenkinsBehaveTestCollector();
        protoType.setName("JenkinsCucumberTest");
        protoType.setCollectorType(CollectorType.Test);
        protoType.setOnline(true);
        protoType.setEnabled(true);
        protoType.getBuildServers().addAll(buildServers);
        return protoType;
    }
    
}
