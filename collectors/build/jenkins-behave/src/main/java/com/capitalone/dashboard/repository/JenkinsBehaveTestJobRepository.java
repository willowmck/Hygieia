package com.capitalone.dashboard.repository;

import com.capitalone.dashboard.model.JenkinsJob;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;

public interface JenkinsBehaveTestJobRepository extends BaseCollectorItemRepository<JenkinsJob> {
    
    @Query(value="{ 'collectorId' : ?0, options.instanceUrl : ?1, enabled: true}")
    List<JenkinsJob> findEnabledJenkinsJobs(ObjectId collectorId, String instanceUrl);
    
    @Query(value="{ 'collectorId' : ?0, options.instanceUrl : ?1, options.jobName : ?2}")
    JenkinsJob findJenkinsJob(ObjectId collectorId, String instanceUrl, String jobName);
}
