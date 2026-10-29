# syntax=docker/dockerfile:1.7
# Aptech-4 mobile toolchain: pinned Flutter + JDK + Android SDK + Shorebird CLI.
# Build for linux/amd64 (Android cmdline-tools). On Apple Silicon this uses emulation.

ARG UBUNTU_IMAGE=ubuntu:24.04
FROM ${UBUNTU_IMAGE}

# Align with current Shorebird stable Flutter when possible (verified at image build).
ARG FLUTTER_VERSION=3.47.8
ARG JAVA_VERSION=17
ARG ANDROID_CMDLINE_TOOLS=13114758
# Flutter 3.47.x doctor requires Android SDK 36 (+ Build-Tools; keep a recent 36.x pin).
ARG ANDROID_PLATFORM=android-36
ARG ANDROID_BUILD_TOOLS=36.0.0
ARG HOST_UID=1000
ARG HOST_GID=1000

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    FLUTTER_HOME=/opt/flutter \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    PUB_CACHE=/home/developer/.pub-cache \
    PATH=/home/developer/.shorebird/bin:/opt/flutter/bin:/opt/flutter/bin/cache/dart-sdk/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

RUN apt-get update && apt-get install -y --no-install-recommends \
      bash \
      ca-certificates \
      curl \
      git \
      unzip \
      xz-utils \
      zip \
      libglu1-mesa \
      "openjdk-${JAVA_VERSION}-jdk-headless" \
      wget \
      sudo \
      locales \
    && rm -rf /var/lib/apt/lists/* \
    && locale-gen en_US.UTF-8 \
    && arch_suffix="$(dpkg --print-architecture)" \
    && ln -sfn "/usr/lib/jvm/java-${JAVA_VERSION}-openjdk-${arch_suffix}" /opt/java-home

ENV JAVA_HOME=/opt/java-home
ENV PATH=/opt/java-home/bin:$PATH

RUN groupadd --gid "${HOST_GID}" developer 2>/dev/null || groupadd developer \
    && useradd --uid "${HOST_UID}" --gid developer --create-home --shell /bin/bash developer 2>/dev/null \
       || useradd --create-home --shell /bin/bash --gid developer developer \
    && echo 'developer ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/developer \
    && chmod 0440 /etc/sudoers.d/developer

# Flutter SDK (pinned tag/branch).
RUN git clone --depth 1 --branch "${FLUTTER_VERSION}" https://github.com/flutter/flutter.git "${FLUTTER_HOME}" \
    && git config --global --add safe.directory "${FLUTTER_HOME}" \
    && chown -R developer:developer "${FLUTTER_HOME}"

# Android command-line tools (pinned zip id).
RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && cd /tmp \
    && wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS}_latest.zip" -O cmdtools.zip \
    && unzip -q cmdtools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -f cmdtools.zip \
    && chown -R developer:developer "${ANDROID_HOME}"

USER developer
WORKDIR /workspace/mobile

# Shorebird installs under ~/.shorebird (keep CLI in image; volume only mounts bin/cache).
RUN curl -fsSL https://shorebird.dev/install.sh -o /tmp/shorebird-install.sh \
    && bash /tmp/shorebird-install.sh \
    && rm -f /tmp/shorebird-install.sh \
    && test -x /home/developer/.shorebird/bin/shorebird

# Licenses: sdkmanager --licenses returns non-zero until accepted; accept then install pins.
RUN mkdir -p /home/developer/.android \
    && yes | sdkmanager --licenses >/home/developer/sdk-licenses.log 2>&1 || true \
    && sdkmanager --install \
         "platform-tools" \
         "platforms;${ANDROID_PLATFORM}" \
         "build-tools;${ANDROID_BUILD_TOOLS}" \
    && test -d "/opt/android-sdk/platforms/${ANDROID_PLATFORM}" \
    && test -d "/opt/android-sdk/build-tools/${ANDROID_BUILD_TOOLS}" \
    && flutter config --no-analytics \
    && flutter precache --android \
    && flutter doctor -v > /home/developer/flutter-doctor-build.log 2>&1 || true \
    && cat /home/developer/flutter-doctor-build.log \
    && shorebird --version | tee /home/developer/shorebird-version-build.log \
    && grep -q "${ANDROID_PLATFORM}" /home/developer/flutter-doctor-build.log || echo "WARN: doctor log may not mention ${ANDROID_PLATFORM} by name"

COPY --chown=developer:developer preflight.sh /usr/local/bin/mobile-preflight
USER root
RUN chmod 755 /usr/local/bin/mobile-preflight
USER developer

LABEL org.opencontainers.image.title="aptech4-mobile-toolchain" \
      org.opencontainers.image.description="Pinned Flutter + Android SDK + Shorebird for Aptech-4" \
      aptech4.flutter.version="${FLUTTER_VERSION}" \
      aptech4.android.platform="${ANDROID_PLATFORM}"

ENTRYPOINT ["/usr/local/bin/mobile-preflight"]
CMD ["bash"]
