# Build Stage
FROM maven:3.9.9-eclipse-temurin-21 AS builder

RUN apt-get update && \
	DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends git && \
	apt-get clean && \
	rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Clone the Account service from GitHub
RUN git clone https://github.com/Deathrow002/Core-Banking-Account.git .

# Build the Account service
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-jammy

# Install wget and curl
RUN apt-get update && \
	DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends wget curl && \
	apt-get clean && \
	rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar account-service.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "account-service.jar"]