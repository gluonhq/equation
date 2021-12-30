module com.gluonhq.wave {
    requires java.logging;
    requires javafx.graphics;

    requires org.whispersystems.metadata;
    requires org.whispersystems.protocol;
    requires org.whispersystems.service;

    requires org.bouncycastle.provider;
    // requires bcprov.jdk15on;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.google.zxing;
    requires okhttp3;

    exports com.gluonhq.equation;
    exports com.gluonhq.equation.log;
    exports com.gluonhq.equation.message;
    exports com.gluonhq.equation.model;
    exports com.gluonhq.equation.provision;
    exports com.gluonhq.equation.util;

    opens com.gluonhq.equation.model to com.fasterxml.jackson.databind;


    // in case we want to deal with System logging itself:
    // provides java.lang.System.LoggerFinder
    //   with com.gluonhq.equation.log.WaveLoggerFinder;
}
