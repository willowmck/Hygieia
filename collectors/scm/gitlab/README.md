<<<<<<< HEAD
# Hygieia SCM Collectors / GitLab

Collect source code details from GitLab based on URL and branch
=======
# Hygieia SCM Collectors / Gitlab (Community Contribution)

Collect source code details from Gitlab based on URL
>>>>>>> capitalone/master

This project uses Spring Boot to package the collector as an executable JAR with dependencies.

## Building and Deploying

To package the collector into an executable JAR file, run:
```bash
mvn install
```

Copy this file to your server and launch it using:
```
java -JAR gitlab-collector.jar
```

## application.properties

<<<<<<< HEAD
You will need to provide an **application.properties** file that contains information about how to connect to the Dashboard MongoDB database instance, as well as properties the Gitlab collector requires. See the Spring Boot [documentation](http://docs.spring.io/spring-boot/docs/current-SNAPSHOT/reference/htmlsingle/#boot-features-external-config-application-property-files) for information about sourcing this properties file.

### Sample application.properties file

```properties
# Database Name
dbname=dashboard

# Database HostName - default is localhost
dbhost=10.0.1.1

# Database Port - default is 27017
dbport=9999

# Database Username - default is blank
dbusername=db

# Database Password - default is blank
dbpassword=dbpass

# Collector schedule (required)
gitlab.cron=0 0/5 * * * *

gitlab.host=gitlab.com

# Maximum number of days to go back in time when fetching commits
gitlab.commitThresholdDays=15
```
=======
You will need to provide an **application.properties** file that contains information about how to connect to the Dashboard MongoDB database instance, as well as properties the Github collector requires. See the Spring Boot [documentation](http://docs.spring.io/spring-boot/docs/current-SNAPSHOT/reference/htmlsingle/#boot-features-external-config-application-property-files) for information about sourcing this properties file.

### Sample application.properties file

```#Database Name 
database=dashboard

#Database HostName - default is IP docker container mongodb
dbhost=localhost

#Database Port - default is 27017
dbport=27017

#Database Username - default is blank
dbusername=db

#Database Password - default is blank
dbpassword=dbpass

#Collector schedule (required)
gitlab.cron=0 0/1 * * * *

#Gitlab server (optional)
gitlab.server=https://gitlab.company.com/

gitlab.branche=master

#set apiKey to use HTTPS Auth
gitlab.apiToken=

gitlab.saveLog=true
```

>>>>>>> capitalone/master
