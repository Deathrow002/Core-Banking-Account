# Build Stage
FROM maven:3.9.9-eclipse-temurin-21 AS builder

RUN apt-get update && \
	DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends git curl && \
	apt-get clean && \
	rm -rf /var/lib/apt/lists/*

WORKDIR /app

ARG GITHUB_TOKEN

# Fetch the parent POM from GitHub and install it
RUN curl -fsSL https://raw.githubusercontent.com/Deathrow002/Core-Banking/master/pom.xml -o /app/pom.xml
RUN mvn clean install -N

# Clone the Account service from GitHub
RUN git clone --branch main --single-branch https://${GITHUB_TOKEN}@github.com/Deathrow002/Core-Banking-Account.git Account

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