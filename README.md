# Gluon Equation
The library part of the Wave Equation

This repository contains the functionality required for the Gluon Wave app,
which can be found at https://github.com/gluonhq/wave-app.

The code in here is bundled into the equation library, which is a modular
Java component.
Gluon Equation provides the bridge between the Gluon Wave app and the
Signal protocol implementations for Java.

## Dependencies

The Signal protocol is implemented in a number of languages, including Java.
The target for this implementation is Android, which is much behind the
level of OpenJDK. Therefore, we use forks of the original repositories,
and optimize them for Java 17+ usage.

The `pom.xml` in this project depends on 

```
        <dependency>
          <groupId>org.whispersystems</groupId>
          <artifactId>signal-service-java</artifactId>
          <version>2.15.4-gluon-7</version>
        </dependency>
```

This artifact is generated from the gluon branch in the repository at 
https://github.com/johanvos/libsignal-service-java, which is a fork of the
original repository at https://github.com/signalapp/libsignal-service-java.
We publish releases in the gluon nexus repository, hence that one needs to be
added to a `pom.xml` if you want to use it.

The `libsignal-service-java` depends on `libsignal-metadata-java` which is in
the gluon branch of https://github.com/johanvos/libsignal-service-java,
which again is a fork of the original Signal repository.

The `libsignal-metadata-java` depends on `libsignal-protocol-java` which is in
the gluon branch of https://github.com/johanvos/libsignal-metadata-java,
which again is a fork of the original Signal repository.

The `libsignal-protocol-java` depends on `curve25519-java` which is in
the gluon branch of https://github.com/johanvos/curve25519-java,
which again is a fork of the original Signal repository.
