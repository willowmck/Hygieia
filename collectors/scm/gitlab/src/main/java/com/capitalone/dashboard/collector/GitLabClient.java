package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.GitLabRepo;

import java.util.List;

/**
 * Client for fetching commit history from GitLab
 */
public interface GitLabClient {

    /**
     * Fetch all of the commits for the provided repository.
     *
     * @param repo 
     * @param firstRun
     * @return all commits in repo
     */

	List<Commit> getCommits(GitLabRepo repo, boolean firstRun);

}
