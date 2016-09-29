package com.capitalone.dashboard.collector;


import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.GitLabRepo;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.CommitRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.capitalone.dashboard.repository.GitLabRepoRepository;

/**
 * CollectorTask that fetches Commit information from GitLab
 */
@Component
public class GitLabCollectorTask extends CollectorTask<Collector> {
    private static final Log LOG = LogFactory.getLog(GitLabCollectorTask.class);

    private final BaseCollectorRepository<Collector> collectorRepository;
    private final GitLabRepoRepository gitLabRepoRepository;
    private final CommitRepository commitRepository;
    private final GitLabClient gitLabClient;
    private final GitLabSettings gitLabSettings;
    private final ComponentRepository dbComponentRepository;

    @Autowired
    public GitLabCollectorTask(TaskScheduler taskScheduler,
                                   BaseCollectorRepository<Collector> collectorRepository,
                                   GitLabRepoRepository gitHubRepoRepository,
                                   CommitRepository commitRepository,
                                   GitLabClient gitHubClient,
                                   GitLabSettings gitHubSettings,
                                   ComponentRepository dbComponentRepository) {
        super(taskScheduler, "GitLab");
        this.collectorRepository = collectorRepository;
        this.gitLabRepoRepository = gitHubRepoRepository;
        this.commitRepository = commitRepository;
        this.gitLabClient = gitHubClient;
        this.gitLabSettings = gitHubSettings;
        this.dbComponentRepository = dbComponentRepository;
    }

    @Override
    public Collector getCollector() {
        Collector protoType = new Collector();
        protoType.setName("GitLab");
        protoType.setCollectorType(CollectorType.SCM);
        protoType.setOnline(true);
        protoType.setEnabled(true);
        return protoType;
    }

    @Override
    public BaseCollectorRepository<Collector> getCollectorRepository() {
        return collectorRepository;
    }

    @Override
    public String getCron() {
        return gitLabSettings.getCron();
    }
    
    private List<CollectorItem> getScmCollectors( com.capitalone.dashboard.model.Component comp ) {
        if (comp.getCollectorItems() != null && !comp.getCollectorItems().isEmpty())
            return comp.getCollectorItems().get(CollectorType.SCM);
        return null;
    }

	/**
	 * Clean up unused deployment collector items
	 *
	 * @param collector
	 *            the {@link Collector}
	 */
	private void clean(Collector collector) {
		Set<ObjectId> uniqueIDs = new HashSet<>();
		/**
		 * Logic: For each component, retrieve the collector item list of the type SCM.
		 * Store their IDs in a unique set ONLY if their collector IDs match with GitLab collectors ID.
		 */
		for (com.capitalone.dashboard.model.Component comp : dbComponentRepository.findAll()) {
            List<CollectorItem> itemList = getScmCollectors(comp);
            if (itemList != null) {
                for (CollectorItem ci : itemList) {
                    if (ci != null && ci.getCollectorId().equals(collector.getId())){
                        uniqueIDs.add(ci.getId());
                    }
                }
            }
		}

		/**
		 * Logic: Get all the collector items from the collector_item collection for this collector.
		 * If their id is in the unique set (above), keep them enabled; else, disable them.
		 */
		List<GitLabRepo> repoList = new ArrayList<>();
		Set<ObjectId> gitID = new HashSet<>();
		gitID.add(collector.getId());
		for (GitLabRepo repo : gitLabRepoRepository.findByCollectorIdIn(gitID)) {
			if (repo != null) {
				repo.setEnabled(uniqueIDs.contains(repo.getId()));
				repoList.add(repo);
			}
		}
		gitLabRepoRepository.save(repoList);
	}


    @Override
    public void collect(Collector collector) {

        logBanner("Starting...");
        long start = System.currentTimeMillis();
        int repoCount = 0;
        int commitCount = 0;

        clean(collector);
        for (GitLabRepo repo : enabledRepos(collector)) {
        	boolean firstRun = false;
        	if (repo.getLastUpdated() == 0) firstRun = true;
        	repo.setLastUpdated(System.currentTimeMillis());
            repo.removeLastUpdateDate();  //moved last update date to collector item. This is to clean old data.
            gitLabRepoRepository.save(repo);
            LOG.debug(repo.getOptions().toString()+"::"+repo.getBranch());
            for (Commit commit : gitLabClient.getCommits(repo, firstRun)) {
            	LOG.debug(commit.getTimestamp()+":::"+commit.getScmCommitLog());
                if (isNewCommit(repo, commit)) {
                    commit.setCollectorItemId(repo.getId());
                    commitRepository.save(commit);
                    commitCount++;
                }
            }

            repoCount++;
        }
        log("Repo Count", start, repoCount);
        log("New Commits", start, commitCount);

        log("Finished", start);
    }


    private List<GitLabRepo> enabledRepos(Collector collector) {
        return gitLabRepoRepository.findEnabledGitLabRepos(collector.getId());
    }

    private boolean isNewCommit(GitLabRepo repo, Commit commit) {
        return commitRepository.findByCollectorItemIdAndScmRevisionNumber(
                repo.getId(), commit.getScmRevisionNumber()) == null;
    }


}
