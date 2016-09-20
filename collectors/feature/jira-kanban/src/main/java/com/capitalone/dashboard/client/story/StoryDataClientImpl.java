/*************************DA-BOARD-LICENSE-START*********************************
 * Copyright 2014 CapitalOne, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************DA-BOARD-LICENSE-END*********************************/

package com.capitalone.dashboard.client.story;

import com.atlassian.jira.rest.client.api.domain.BasicProject;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.User;
import com.capitalone.dashboard.client.JiraClient;
import com.capitalone.dashboard.model.Feature;
import com.capitalone.dashboard.model.FeatureStatus;
import com.capitalone.dashboard.repository.FeatureCollectorRepository;
import com.capitalone.dashboard.repository.FeatureRepository;
import com.capitalone.dashboard.util.ClientUtil;
import com.capitalone.dashboard.util.FeatureCollectorConstants;
import com.capitalone.dashboard.util.CoreFeatureSettings;
import com.capitalone.dashboard.util.DateUtil;
import com.capitalone.dashboard.util.FeatureSettings;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * This is the primary implemented/extended data collector for the feature
 * collector. This will get data from the source system, but will grab the
 * majority of needed data and aggregate it in a single, flat MongoDB collection
 * for consumption.
 * 
 * @author kfk884
 * 
 */
public class StoryDataClientImpl implements StoryDataClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(StoryDataClientImpl.class);
	private static final ClientUtil TOOLS = ClientUtil.getInstance();
	private static final String TO_DO = "To Do";
	private static final String IN_PROGRESS = "In Progress";
	private static final String DONE = "Done";

	private final FeatureSettings featureSettings;
	private final FeatureRepository featureRepo;
	private final FeatureCollectorRepository featureCollectorRepository;
	private final JiraClient jiraClient;
	
	// epicId : list of epics
	//private final Map<String, Issue> epicCache;
	private final Set<String> todoCache;
	private final Set<String> inProgressCache;
	private final Set<String> doneCache;

	/**
	 * Extends the constructor from the super class.
     * @param coreFeatureSettings 
     * @param featureSettings
     * @param featureRepository
     * @param featureCollectorRepository
     * @param jiraClient
	 */
	public StoryDataClientImpl(CoreFeatureSettings coreFeatureSettings, FeatureSettings featureSettings, 
			FeatureRepository featureRepository, FeatureCollectorRepository featureCollectorRepository,
			JiraClient jiraClient) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Constructing data collection for the feature widget, story-level data...");
		}

		this.featureSettings = featureSettings;
		this.featureRepo = featureRepository;
		this.featureCollectorRepository = featureCollectorRepository;
		this.jiraClient = jiraClient;
		
		//this.epicCache = new HashMap<>();
		
		todoCache = buildStatusCache(coreFeatureSettings.getTodoStatuses());
		inProgressCache = buildStatusCache(coreFeatureSettings.getDoingStatuses());
		doneCache = buildStatusCache(coreFeatureSettings.getDoneStatuses());
	}

	/**
	 * Explicitly updates queries for the source system, and initiates the
	 * update to MongoDB from those calls.
     * @param project
	 */
    @Override
	public int updateStoryInformation(String project) {
		int count = 0;
		int pageSize = jiraClient.getPageSize();
				
		updateStatuses();

		for (int i = 0; ; i += pageSize) {
			if (LOGGER.isDebugEnabled()) 
				LOGGER.debug("Obtaining story information starting at index " + i + "...");
			
			long queryStart = System.currentTimeMillis();
			List<Issue> issues = jiraClient.getIssues(project, i);
			if (LOGGER.isDebugEnabled()) 
				LOGGER.debug("Story information query took " + (System.currentTimeMillis() - queryStart) + " ms");
			
			if (issues != null && !issues.isEmpty()) {
				updateMongoInfo(issues);
				count += issues.size();
			}
			
			// will result in an extra call if number of results == pageSize
			// but I would rather do that then complicate the jira client implementation
			if (issues == null || issues.size() < pageSize) 
				break;
			
		}
		
		return count;
	}

	/**
	 * Updates the MongoDB with a JSONArray received from the source system
	 * back-end with story-based data.
	 * 
	 * @param currentPagedJiraRs
	 *            A list response of Jira issues from the source system
	 */
	@SuppressWarnings({ "PMD.AvoidDeeplyNestedIfStmts", "PMD.NPathComplexity" })
	private void updateMongoInfo(List<Issue> currentPagedJiraRs) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Size of paged Jira response: " + (currentPagedJiraRs == null? 0 : currentPagedJiraRs.size()));
		}
		
		if (currentPagedJiraRs != null) {
			List<Feature> featuresToSave = new ArrayList<>();
			ObjectId jiraFeatureId = featureCollectorRepository.findByName(FeatureCollectorConstants.JIRA_KANBAN).getId();
			
			for (Issue issue : currentPagedJiraRs) {
				String issueId = TOOLS.sanitizeResponse(issue.getId());
				
				Feature feature = findOneFeature(issueId);
				if (feature == null) 
					 feature = new Feature();
                
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(String.format("[%-12s] %s", 
                            TOOLS.sanitizeResponse(issue.getKey()),
                            TOOLS.sanitizeResponse(issue.getSummary())));
                }
					
				// collectorId
				feature.setCollectorId(jiraFeatureId);
					
				// ID
				feature.setsId(TOOLS.sanitizeResponse(issue.getId()));

				processFeatureData(feature, issue);
                
                processSprintData(feature);
					
				featuresToSave.add(feature);
				
			}
			
			// Saving back to MongoDB
			featureRepo.save(featuresToSave);
		}
	}
	
	private void processFeatureData(Feature feature, Issue issue) {
		BasicProject project = issue.getProject();
		String status = this.toCanonicalFeatureStatus(issue.getStatus().getName());
		String changeDate = issue.getUpdateDate().toString();
		
		// sNumber
		feature.setsNumber(TOOLS.sanitizeResponse(issue.getKey()));

		// sName
		feature.setsName(TOOLS.sanitizeResponse(issue.getSummary()));

		// sStatus
		feature.setsStatus(TOOLS.sanitizeResponse(status));

		// sState
		feature.setsState(TOOLS.sanitizeResponse(status));
            
        // Use story points and just increment it for Kanban
        feature.setsEstimate("1");

		// sChangeDate
		feature.setChangeDate(TOOLS.toCanonicalDate(TOOLS.sanitizeResponse(changeDate)));

		// IsDeleted - does not exist for Jira
		feature.setIsDeleted("False");

		// sProjectID
		feature.setsProjectID(TOOLS.sanitizeResponse(project.getKey()));

		// sProjectName
		feature.setsProjectName(TOOLS.sanitizeResponse(project.getName()));

		// sProjectBeginDate - does not exist in Jira
		feature.setsProjectBeginDate("");

		// sProjectEndDate - does not exist in Jira
		feature.setsProjectEndDate("");

		// sProjectChangeDate - does not exist for this asset level in Jira
		feature.setsProjectChangeDate("");

		// sProjectState - does not exist in Jira
		feature.setsProjectState("");

		// sProjectIsDeleted - does not exist in Jira
		feature.setsProjectIsDeleted("False");

		// sProjectPath - does not exist in Jira
		feature.setsProjectPath("");
		
		// sTeamID
		feature.setsTeamID(TOOLS.sanitizeResponse(project.getId()));

		// sTeamName
		feature.setsTeamName(TOOLS.sanitizeResponse(project.getName()));
		
		// sTeamChangeDate - not able to retrieve at this asset level from Jira
		feature.setsTeamChangeDate("");

		// sTeamAssetState
		feature.setsTeamAssetState("");

		// sTeamIsDeleted
		feature.setsTeamIsDeleted("False");

		// sOwnersState - does not exist in Jira at this level
		feature.setsOwnersState(Arrays.asList("Active"));

		// sOwnersChangeDate - does not exist in Jira
		feature.setsOwnersChangeDate(TOOLS.toCanonicalList(Collections.<String>emptyList()));

		// sOwnersIsDeleted - does not exist in Jira
		feature.setsOwnersIsDeleted(TOOLS.toCanonicalList(Collections.<String>emptyList()));
	}
	
	private void processSprintData(Feature feature) {
        /*
         * For Kanban, associate a generic, never-ending
         * kanban 'sprint'
         */
        feature.setsSprintID(FeatureCollectorConstants.KANBAN_SPRINT_ID);
        feature.setsSprintName(FeatureCollectorConstants.KANBAN_SPRINT_ID);
        feature.setsSprintBeginDate(FeatureCollectorConstants.KANBAN_START_DATE);
        feature.setsSprintEndDate(FeatureCollectorConstants.KANBAN_END_DATE);
        feature.setsSprintAssetState("Active");

		// sSprintChangeDate - does not exist in Jira
		feature.setsSprintChangeDate("");

		// sSprintIsDeleted - does not exist in Jira
		feature.setsSprintIsDeleted("False");
	}
	
	private void processAssigneeData(Feature feature, User assignee) {
		if (assignee != null) {
			// sOwnersID
			List<String> assigneeKey = new ArrayList<String>();
			// sOwnersShortName
			// sOwnersUsername
			List<String> assigneeName = new ArrayList<String>();
			if (!assignee.getName().isEmpty() && (assignee.getName() != null)) {
				assigneeKey.add(TOOLS.sanitizeResponse(assignee.getName()));
				assigneeName.add(TOOLS.sanitizeResponse(assignee.getName()));

			} else {
				assigneeKey = new ArrayList<String>();
				assigneeName = new ArrayList<String>();
			}
			feature.setsOwnersShortName(assigneeName);
			feature.setsOwnersUsername(assigneeName);
			feature.setsOwnersID(assigneeKey);

			// sOwnersFullName
			List<String> assigneeDisplayName = new ArrayList<>();
			if (!assignee.getDisplayName().isEmpty() && (assignee.getDisplayName() != null)) {
				assigneeDisplayName.add(TOOLS.sanitizeResponse(assignee.getDisplayName()));
			} else {
				assigneeDisplayName.add("");
			}
			feature.setsOwnersFullName(assigneeDisplayName);
		} else {
			feature.setsOwnersUsername(new ArrayList<>());
			feature.setsOwnersShortName(new ArrayList<>());
			feature.setsOwnersID(new ArrayList<>());
			feature.setsOwnersFullName(new ArrayList<>());
		}
	}

	/**
	 * ETL for converting any number of custom Jira statuses to a reduced list
	 * of generally logical statuses used by Hygieia
	 * 
	 * @param nativeStatus
	 *            The status label as native to Jira
	 * @return A Hygieia-canonical status, as defined by a Core enum
	 */
	private String toCanonicalFeatureStatus(String nativeStatus) {
		// default to backlog
		String canonicalStatus = FeatureStatus.BACKLOG.getStatus();
		
		if (nativeStatus != null) {
			String nsLower = nativeStatus.toLowerCase(Locale.getDefault());
			
			if (todoCache.contains(nsLower)) {
				canonicalStatus = FeatureStatus.BACKLOG.getStatus();
			} else if (inProgressCache.contains(nsLower)) {
				canonicalStatus = FeatureStatus.IN_PROGRESS.getStatus();
			} else if (doneCache.contains(nsLower)) {
				canonicalStatus = FeatureStatus.DONE.getStatus();
			}
		}
		
		return canonicalStatus;
	}
	
	/**
	 * Retrieves the maximum change date for a given query.
	 * 
	 * @return A list object of the maximum change date
	 */
	public String getMaxChangeDate() {
		String data = null;

		try {
			List<Feature> response = featureRepo
					.findTopByCollectorIdAndChangeDateGreaterThanOrderByChangeDateDesc(
							featureCollectorRepository.findByName(FeatureCollectorConstants.JIRA_KANBAN).getId(),
							featureSettings.getDeltaStartDate());
			if ((response != null) && !response.isEmpty()) {
				data = response.get(0).getChangeDate();
			}
		} catch (Exception e) {
			LOGGER.error("There was a problem retrieving or parsing data from the local "
					+ "repository while retrieving a max change date\nReturning null", e);
		}

		return data;

	}
	
	private String getChangeDateMinutePrior(String changeDateISO) {
		int priorMinutes = this.featureSettings.getScheduledPriorMin();
		return DateUtil.toISODateRealTimeFormat(DateUtil.getDatePriorToMinutes(
				DateUtil.fromISODateTimeFormat(changeDateISO), priorMinutes));
	}
	
	private Feature findOneFeature(String featureId) {
		List<Feature> features = featureRepo.getFeatureIdById(featureId);
		
		// Not sure of the state of the data
		if (features.size() > 1) {
			LOGGER.warn("More than one collector item found for scopeId " + featureId);
		}
		
		if (!features.isEmpty()) {
			return features.get(0);
		}
		
		return null;
	}
	
	private Map<String, IssueField> buildFieldMap(Iterable<IssueField> fields) {
		Map<String, IssueField> rt = new HashMap<>();
		
		if (fields != null) {
			for (IssueField issueField : fields) {
				rt.put(issueField.getId(), issueField);
			}
		}
		
		return rt;
	}
	
	private Set<String> buildStatusCache(List<String> statuses) {
		Set<String> rt = new HashSet<>();
		
		if (statuses != null) {
			for (String status : statuses) {
				rt.add(status.toLowerCase(Locale.getDefault()));
			}
		}
		
		return rt;
	}
	
	private void updateStatuses() {
		Map<String, String> statusMap = jiraClient.getStatusMapping();
		for (String status : statusMap.keySet()) {
			String statusCategory = statusMap.get(status);
			if (TO_DO.equals(statusCategory)) {
				todoCache.add(status.toLowerCase(Locale.getDefault()));
			} else if (IN_PROGRESS.equals(statusCategory)) {
				inProgressCache.add(status.toLowerCase(Locale.getDefault()));
			} else if (DONE.equals(statusCategory)) {
				doneCache.add(status.toLowerCase(Locale.getDefault()));
			}
		}
	}
}
