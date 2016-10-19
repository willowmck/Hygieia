package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.Build;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.JenkinsBehaveTestCollector;
import com.capitalone.dashboard.model.JenkinsJob;
import com.capitalone.dashboard.model.TestResult;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.JenkinsBehaveTestCollectorRepository;
import com.capitalone.dashboard.repository.JenkinsBehaveTestJobRepository;
import com.capitalone.dashboard.repository.TestResultRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class JenkinsBehaveTestCollectorTask extends
        CollectorTask<JenkinsBehaveTestCollector> {

    private final JenkinsBehaveTestCollectorRepository jenkinsBehaveTestCollectorRepository;
    private final JenkinsBehaveTestJobRepository jenkinsBehaveTestJobRepository;
    private final TestResultRepository testResultRepository;
    private final JenkinsClient jenkinsClient;
    private final JenkinsSettings jenkinsBehaveTestSettings;
    private final ComponentRepository dbComponentRepository;
    
    @Autowired
    public JenkinsBehaveTestCollectorTask(
            TaskScheduler taskScheduler,
            JenkinsBehaveTestCollectorRepository jenkinsBehaveTestCollectorRepository,
            JenkinsBehaveTestJobRepository jenkinsBehaveTestJobRepository,
            TestResultRepository testResultRepository,
            JenkinsClient jenkinsBehaveTestClient,
            JenkinsSettings jenkinsBehaveTestSettings,
            ComponentRepository dbComponentRepository) {
        super(taskScheduler, "JenkinsCucumberTest");
        this.jenkinsBehaveTestCollectorRepository = jenkinsBehaveTestCollectorRepository;
        this.jenkinsBehaveTestJobRepository = jenkinsBehaveTestJobRepository;
        this.testResultRepository = testResultRepository;
        this.jenkinsClient = jenkinsBehaveTestClient;
        this.jenkinsBehaveTestSettings = jenkinsBehaveTestSettings;
        this.dbComponentRepository = dbComponentRepository;
    }

    @Override
    public JenkinsBehaveTestCollector getCollector() {
        return JenkinsBehaveTestCollector
                .prototype(jenkinsBehaveTestSettings.getServers());
    }

    @Override
    public BaseCollectorRepository<JenkinsBehaveTestCollector> getCollectorRepository() {
        return jenkinsBehaveTestCollectorRepository;
    }

    @Override
    public String getCron() {
        return jenkinsBehaveTestSettings.getCron();
    }

    @Override
    public void collect(JenkinsBehaveTestCollector collector) {

        long start = System.currentTimeMillis();

        clean(collector);

        for (String instanceUrl : collector.getBuildServers()) {
            logBanner(instanceUrl);

            Map<JenkinsJob, Set<Build>> buildsByJob = jenkinsClient
                    .getInstanceJobs(instanceUrl);
            log("Fetched jobs", start);

            addNewJobs(buildsByJob.keySet(), collector, instanceUrl);
            
            List<JenkinsJob> enabledJobs = enabledJobs(collector, instanceUrl);
            if ( ! enabledJobs.isEmpty())
            {
                addNewTestSuites(enabledJobs, instanceUrl); 
            }
            else
            {
            	log("WARNING: No Enabled Jobs found with artifacts pattern: " + jenkinsBehaveTestSettings.getBehaveJsonRegex());
            }
            log("Finished", start);
        }
    }

    /**
     * Adds new {@link JenkinsJob}s to the database as disabled jobs.
     *
     * @param jobs      list of {@link JenkinsJob}s
     * @param collector the {@link JenkinsBehaveTestCollector}
     */
    private void addNewJobs(Set<JenkinsJob> jobs,
                            JenkinsBehaveTestCollector collector, 
                            String instanceUrl) {
        long start = System.currentTimeMillis();
        int count = 0;

        for (JenkinsJob job : jobs) {
            if (jenkinsClient.buildHasBehaveResults(job.getJobUrl(), instanceUrl)
                    && isNewJob(collector, job)) {
                job.setCollectorId(collector.getId());
                job.setEnabled(false); // Do not enable for collection. Will be
                // enabled when added to dashboard
                job.setDescription(job.getJobName());
                jenkinsBehaveTestJobRepository.save(job);
                count++;
            }
        }
        log("New jobs", start, count);
    }

    private void addNewTestSuites(List<JenkinsJob> enabledJobs, String instanceUrl) {
        long start = System.currentTimeMillis();
        int count = 0;
        for (JenkinsJob job : enabledJobs) {
            Build buildSummary = jenkinsClient.getLastSuccessfulBuild(job.getJobUrl(), instanceUrl);
			if (isNewBehaveResult(job, buildSummary)) {
                // Obtain the Test Result
                TestResult result = jenkinsClient
                        .getBehaveTestResult(job.getJobUrl(), instanceUrl);
                if (result != null) {
                    result.setCollectorItemId(job.getId());
                    result.setTimestamp(System.currentTimeMillis());
                    testResultRepository.save(result);
                    count++;
                }
            }
        }
        log("New test suites", start, count);
    }

    private boolean isNewBehaveResult(JenkinsJob job, Build build) {
        return testResultRepository.findByCollectorItemIdAndExecutionId(
                job.getId(), build.getNumber()) == null;
    }

    private List<JenkinsJob> enabledJobs(
            JenkinsBehaveTestCollector collector, String instanceUrl) {
        return jenkinsBehaveTestJobRepository.findEnabledJenkinsJobs(
                collector.getId(), instanceUrl);
        
    }

    private boolean isNewJob(JenkinsBehaveTestCollector collector,
                             JenkinsJob job) {
        return jenkinsBehaveTestJobRepository.findJenkinsJob(
                collector.getId(), job.getInstanceUrl(), job.getJobName()) == null;
        
    }

    /**
     * Clean up unused hudson/jenkins collector items
     *
     * @param collector the collector
     */

    private void clean(JenkinsBehaveTestCollector collector) {

        // First delete jobs that will be no longer collected because servers have moved etc.
        deleteUnwantedJobs(collector);

        Set<ObjectId> uniqueIDs = new HashSet<>();
        for (com.capitalone.dashboard.model.Component comp : dbComponentRepository
                .findAll()) {
            if (comp.getCollectorItems() == null
                    || comp.getCollectorItems().isEmpty()) continue;
            List<CollectorItem> itemList = comp.getCollectorItems().get(
                    CollectorType.Test);
            if (itemList == null) continue;
            for (CollectorItem ci : itemList) {
                if (ci != null
                        && ci.getCollectorId().equals(collector.getId())) {
                    uniqueIDs.add(ci.getId());
                }
            }
        }
        
    }

    private void deleteUnwantedJobs(JenkinsBehaveTestCollector collector) {

        List<JenkinsJob> deleteJobList = new ArrayList<>();
        Set<ObjectId> udId = new HashSet<>();
        udId.add(collector.getId());
        for (JenkinsJob job : jenkinsBehaveTestJobRepository.findByCollectorIdIn(udId)) {
            if (!collector.getBuildServers().contains(job.getInstanceUrl()) ||
                    (!job.getCollectorId().equals(collector.getId()))) {
                deleteJobList.add(job);
            }
        }

        jenkinsBehaveTestJobRepository.delete(deleteJobList);
    }

    @SuppressWarnings("unused")
	private Set<Build> nullSafe(Set<Build> builds) {
        return builds == null ? new HashSet<Build>() : builds;
    }
    
}
