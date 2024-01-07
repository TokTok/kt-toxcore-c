FROM ubuntu:22.04

ENV DEBIAN_FRONTEND="noninteractive"

RUN apt-get update && apt-get install --no-install-recommends -y \
 build-essential \
 ca-certificates \
 cmake \
 curl \
 default-jdk \
 git \
 libopus-dev \
 libsodium-dev \
 libvpx-dev \
 ninja-build \
 pkg-config \
 unzip \
 wget \
 zip \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN curl -s "https://get.sdkman.io" | bash

WORKDIR /tmp/boot
COPY boot/ /tmp/boot
RUN . /root/.sdkman/bin/sdkman-init.sh \
 && sdk install gradle 8.5
RUN . /root/.sdkman/bin/sdkman-init.sh \
 && gradle nativeBinaries

WORKDIR /root
RUN git clone --depth=1 --recursive --shallow-submodules https://github.com/TokTok/c-toxcore

WORKDIR /root/c-toxcore/_build
RUN cmake .. -GNinja -DENABLE_STATIC=OFF -DMIN_LOGGER_LEVEL=TRACE -DAUTOTEST=OFF && ninja install

WORKDIR /root
COPY build.gradle.kts /root/
COPY src/ /root/src/
RUN . /root/.sdkman/bin/sdkman-init.sh \
 && gradle nativeBinaries
RUN build/bin/native/debugExecutable/root.kexe
