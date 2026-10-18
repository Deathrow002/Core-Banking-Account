# Build Stage
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Build with the repository root as context, including uncommitted local changes.
COPY pom.xml /app/pom.xml
RUN mvn clean install -N
COPY Account/pom.xml /app/Account/pom.xml
COPY Account/src /app/Account/src

# Build the Account service
RUN mkdir -p Account/src/main/avro Account/src/test/avro
RUN mvn clean package -DskipTests -f Account/pom.xml

# Runtime Stage
FROM eclipse-temurin:21-jre-jammy

# Install wget and curl
RUN apt-get update && \
	DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends wget curl && \
	apt-get upgrade -y && \
	apt-get clean && \
	rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/Account/target/*.jar account-service.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "account-service.jar"]