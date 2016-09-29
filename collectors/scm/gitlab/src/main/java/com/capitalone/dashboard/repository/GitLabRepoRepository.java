package com.capitalone.dashboard.repository;

import com.capitalone.dashboard.model.GitLabRepo;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface GitLabRepoRepository extends BaseCollectorItemRepository<GitLabRepo> {

    @Query(value="{ 'collectorId' : ?0, options.repoUrl : ?1, options.branch : ?2}")
    GitLabRepo findGitLabRepo(ObjectId collectorId, String url, String branch);

    @Query(value="{ 'collectorId' : ?0, enabled: true}")
    List<GitLabRepo> findEnabledGitLabRepos(ObjectId collectorId);
}
