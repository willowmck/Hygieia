package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.CommitType;
import com.capitalone.dashboard.model.GitLabRepo;
import com.capitalone.dashboard.util.Supplier;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;


@Component
public class DefaultGitLabClient implements GitLabClient {
	private static final Log LOG = LogFactory.getLog(DefaultGitLabClient.class);

	private final GitLabSettings settings;

	private final RestOperations restOperations;
	private static final String SEGMENT_API = "/api/v3/projects/";
	private static final int FIRST_RUN_HISTORY_DEFAULT = 14;

	@Autowired
	public DefaultGitLabClient(GitLabSettings settings,
			Supplier<RestOperations> restOperationsSupplier) {
		this.settings = settings;
		this.restOperations = restOperationsSupplier.get();
	}
    
    private String getApiUrl(GitLabRepo repo) {
        
		// format URL
		String repoUrl = (String) repo.getOptions().get("url");
		if (repoUrl.endsWith(".git")) {
			repoUrl = repoUrl.substring(0, repoUrl.lastIndexOf(".git"));
		}
		URL url;
		String hostName = "";
		String protocol = "";
		try {
			url = new URL(repoUrl);
			hostName = url.getHost();
			protocol = url.getProtocol();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			LOG.error(e.getMessage());
		}
		String hostUrl = protocol + "://" + hostName + "/";
		String repoName = repoUrl.substring(hostUrl.length(), repoUrl.length());
		String apiUrl;
        apiUrl = protocol + "://" + hostName + SEGMENT_API + repoName;
        LOG.debug("API URL IS:"+apiUrl);
        return apiUrl;
    }

	@Override
	public List<Commit> getCommits(GitLabRepo repo, boolean firstRun) {

		List<Commit> commits = new ArrayList<>();

		// format URL
		String apiUrl = getApiUrl(repo);
        
		Date dt;
		if (firstRun) {
			int firstRunDaysHistory = settings.getFirstRunHistoryDays();
			if (firstRunDaysHistory > 0) {
				dt = getDate(new Date(), -firstRunDaysHistory, 0);
			} else {
				dt = getDate(new Date(), -FIRST_RUN_HISTORY_DEFAULT, 0);
			}
		} else {
			dt = getDate(new Date(repo.getLastUpdated()), 0, -10);
		}
		Calendar calendar = new GregorianCalendar();
		TimeZone timeZone = calendar.getTimeZone();
		Calendar cal = Calendar.getInstance(timeZone);
		cal.setTime(dt);

		String queryUrl = apiUrl.concat("/repository/commits?ref_name=" + repo.getBranch());

		boolean lastPage = false;
		int pageNumber = 1;
		String queryUrlPage = queryUrl;
		while (!lastPage) {
			try {
				ResponseEntity<String> response = makeRestCall(queryUrlPage, settings.getAuthToken());
				JSONArray jsonArray = parseAsArray(response);
				for (Object item : jsonArray) {
					JSONObject jsonObject = (JSONObject) item;
					String sha = str(jsonObject, "id");
					String message = str(jsonObject, "title");
					String author = str(jsonObject, "author_name");
					long timestamp = new DateTime(str(jsonObject, "created_at"))
							.getMillis();
                    JSONArray parents = (JSONArray) jsonObject.get("parents");
					List<String> parentShas = new ArrayList<>();
					if (parents != null) {
						for (Object parentObj : parents) {
							parentShas.add(str((JSONObject)parentObj, "sha"));
						}
					}
                    
					Commit commit = new Commit();
					commit.setTimestamp(System.currentTimeMillis());
					commit.setScmUrl(repo.getRepoUrl());
                    commit.setScmBranch(repo.getBranch());
					commit.setScmRevisionNumber(sha);
					commit.setScmParentRevisionNumbers(parentShas);
					commit.setScmAuthor(author);
					commit.setScmCommitLog(message);
					commit.setScmCommitTimestamp(timestamp);
					commit.setNumberOfChanges(1);
                    commit.setType(getCommitType(0, message));
					commits.add(commit);
				}
				if (CollectionUtils.isEmpty(jsonArray)) {
					lastPage = true;
				} else {
					lastPage = isThisLastPage(response);
					pageNumber++;
					queryUrlPage = queryUrl + "&page=" + pageNumber;
				}

			} catch (RestClientException re) {
				LOG.error(re.getMessage() + ":" + queryUrl);
				lastPage = true;

			}
		}
		return commits;
	}

	private CommitType getCommitType (int parentSize, String commitMessage ) {
	    if (parentSize > 1) return CommitType.Merge;
        if (settings.getNotBuiltCommits() == null) return CommitType.New;
        for (String s : settings.getNotBuiltCommits()) {
            if (commitMessage.contains(s)) {
                return CommitType.NotBuilt;
            }
        }
        return CommitType.New;
    }

	private Date getDate(Date dateInstance, int offsetDays, int offsetMinutes) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(dateInstance);
		cal.add(Calendar.DATE, offsetDays);
		cal.add(Calendar.MINUTE, offsetMinutes);
		return cal.getTime();
	}

	private boolean isThisLastPage(ResponseEntity<String> response) {
		HttpHeaders header = response.getHeaders();
		List<String> link = header.get("Link");
		if (link == null || link.isEmpty()) {
			return true;
		} else {
			for (String l : link) {
				if (l.contains("rel=\"next\"")) {
					return false;
				}

			}
		}
		return true;
	}

	private ResponseEntity<String> makeRestCall(String url, String token) {
		// Basic Auth only.
		if (!"".equals(token)) {
			return restOperations.exchange(url, HttpMethod.GET,
					new HttpEntity<>(createHeaders(token)),
					String.class);

		} else {
			return restOperations.exchange(url, HttpMethod.GET, null,
					String.class);
		}

	}

	private HttpHeaders createHeaders(final String token) {

		HttpHeaders headers = new HttpHeaders();
		headers.set("PRIVATE-TOKEN", token);
		return headers;
	}

	private JSONArray parseAsArray(ResponseEntity<String> response) {
		try {
			return (JSONArray) new JSONParser().parse(response.getBody());
		} catch (ParseException pe) {
			LOG.error(pe.getMessage());
		}
		return new JSONArray();
	}

	private String str(JSONObject json, String key) {
		Object value = json.get(key);
		return value == null ? null : value.toString();
	}

}
