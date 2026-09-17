# Stage 1: Build stage
FROM public.ecr.aws/docker/library/maven:3.9.7-amazoncorretto-21 AS build

COPY commons/pom.xml commons/pom.xml
COPY commons/src commons/src
RUN mvn -f commons/pom.xml install -DskipTests

COPY kafkaservice-client/pom.xml kafkaservice-client/pom.xml
COPY kafkaservice-client/src kafkaservice-client/src
RUN mvn -f kafkaservice-client/pom.xml install -DskipTests

COPY orderservice/pom.xml ./
COPY orderservice/src src

RUN mvn clean install -DskipTests -Dmaven.test.skip=true

# Stage 2: Run stage
FROM public.ecr.aws/amazoncorretto/amazoncorretto:21

WORKDIR orderservice

COPY --from=build target/*-exec.jar orderservice.jar

EXPOSE 1121

ENTRYPOINT ["java", "-jar", "orderservice.jar"]
