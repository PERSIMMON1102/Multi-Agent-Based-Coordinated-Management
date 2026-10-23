# Backend Dockerfile - Multi-stage build
ARG MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17
FROM ${MAVEN_IMAGE} AS builder
WORKDIR /app

# Copy Maven settings with Aliyun mirror for faster downloads
COPY settings.xml /root/.m2/settings.xml

# Install JADE 4.5.0 from vendored artifacts to keep Docker builds independent from external hosts
COPY third_party/jade/4.5.0/jade-4.5.0.jar /tmp/jade-4.5.0.jar
COPY third_party/jade/4.5.0/jade-4.5.0.pom /tmp/jade-4.5.0.pom
RUN \
    mvn install:install-file \
      -Dfile=/tmp/jade-4.5.0.jar \
      -DpomFile=/tmp/jade-4.5.0.pom \
      -DgroupId=com.tilab.jade \
      -DartifactId=jade \
      -Dversion=4.5.0 \
      -Dpackaging=jar -B

COPY pom.xml .
COPY cdm-common/pom.xml cdm-common/
COPY cdm-model/pom.xml cdm-model/
COPY cdm-agent/pom.xml cdm-agent/
COPY cdm-service/pom.xml cdm-service/
COPY cdm-web/pom.xml cdm-web/
RUN mvn dependency:go-offline -B || true

COPY cdm-common/src cdm-common/src
COPY cdm-model/src cdm-model/src
COPY cdm-agent/src cdm-agent/src
COPY cdm-service/src cdm-service/src
COPY cdm-web/src cdm-web/src
RUN mvn package -Dmaven.test.skip=true -B

# Runtime stage
ARG MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17
FROM ${MAVEN_IMAGE}
WORKDIR /app
COPY --from=builder /app/cdm-web/target/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
