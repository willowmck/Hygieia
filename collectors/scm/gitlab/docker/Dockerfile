
FROM docker.io/java:openjdk-8-jdk

MAINTAINER Hygieia@capitalone.com

RUN \
  mkdir /hygieia

COPY *.jar /hygieia/
<<<<<<< HEAD
COPY github-properties-builder.sh /hygieia/

=======
COPY application.properties /hygieia/
>>>>>>> capitalone/master
WORKDIR /hygieia

VOLUME ["/hygieia/logs"]

<<<<<<< HEAD
CMD ./github-properties-builder.sh && \
  java -jar github-scm-collector*.jar --spring.config.location=/hygieia/hygieia-github-scm-collector.properties
=======
CMD java -jar gitlab-git-collector*.jar --spring.config.location=/hygieia/application.properties
>>>>>>> capitalone/master

