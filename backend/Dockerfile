# Build Stage
FROM docker.io/gradle:jdk25 AS build
WORKDIR /app
COPY . .
# Skip tests during build as they are run separately
RUN ./gradlew shadowJar --no-daemon

# Runtime Stage
FROM scratch

# Shared Libraries
COPY --from=build /lib/x86_64-linux-gnu/libc.so.6 /lib/x86_64-linux-gnu/libc.so.6
COPY --from=build /lib/x86_64-linux-gnu/libdl.so.2 /lib/x86_64-linux-gnu/libdl.so.2
COPY --from=build /lib/x86_64-linux-gnu/libpthread.so.0 /lib/x86_64-linux-gnu/libpthread.so.0
COPY --from=build /lib/x86_64-linux-gnu/librt.so.1 /lib/x86_64-linux-gnu/librt.so.1
COPY --from=build /lib/x86_64-linux-gnu/libm.so.6 /lib/x86_64-linux-gnu/libm.so.6
COPY --from=build /lib/x86_64-linux-gnu/libstdc++.so.6.0.33 /lib/x86_64-linux-gnu/libstdc++.so.6
COPY --from=build /lib/x86_64-linux-gnu/libgcc_s.so.1 /lib/x86_64-linux-gnu/libgcc_s.so.1
COPY --from=build /lib/x86_64-linux-gnu/libz.so.1.3 /lib/x86_64-linux-gnu/libz.so.1
COPY --from=build /lib/x86_64-linux-gnu/ld-linux-x86-64.so.2 /lib64/ld-linux-x86-64.so.2
COPY --from=build /etc/ld.so.cache /etc/ld.so.cache

# JDK
COPY --from=build /opt/java/openjdk /opt/java/openjdk

# Application
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
COPY src/main/resources/schemas /app/schemas

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH
ENV SCHEMA_ROOT=/app/schemas

CMD ["/opt/java/openjdk/bin/java", "-jar", "/app/app.jar"]
