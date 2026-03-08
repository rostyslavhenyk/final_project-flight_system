FROM mcr.microsoft.com/devcontainers/java:1-21-bullseye

# Install additional tools for HCI development
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends \
       curl \
       wget \
       git \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Set Gradle cache location to persist across builds
ENV GRADLE_USER_HOME=/workspace/.gradle

# Create data directory with correct permissions
RUN mkdir -p /workspace/data && chown -R vscode:vscode /workspace/data

USER vscode

# Warmup: Download Gradle wrapper in advance
WORKDIR /tmp
RUN wget -q https://services.gradle.org/distributions/gradle-8.5-bin.zip && \
    unzip -q gradle-8.5-bin.zip && \
    rm gradle-8.5-bin.zip

WORKDIR /workspace
