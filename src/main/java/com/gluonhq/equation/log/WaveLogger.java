/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.log;

import java.text.MessageFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import org.whispersystems.libsignal.logging.SignalProtocolLogger;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;

public class WaveLogger implements System.Logger, SignalProtocolLogger {

    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
    private int myLevel;
    private Level sysLevel;
    private final String WAVE_LOGGER = "WaveLogger";

    public WaveLogger() {
        this(Level.INFO);
    }

    public WaveLogger(Level level) {
        setLevel(level);
        SignalProtocolLoggerProvider.setProvider(this);
    }

    public void setLevel(Level level) {
        System.err.println("SETLEVEL for " + this + " to " + level);
        this.sysLevel = level;
        this.myLevel = 4;
        switch (level) {
            case TRACE:
                myLevel = 2;
                break;
            case DEBUG:
                myLevel = 3;
                break;
            case INFO:
                myLevel = 4;
                break;
            case WARNING:
                myLevel = 5;
                break;
            case ERROR:
                myLevel = 6;
        }
        System.err.println("after setlevel, mylevel = " + myLevel);
    }

    @Override
    public void log(int priority, String tag, String message) {
        if (priority >= myLevel) {
            String format = dtf.format(LocalTime.now());
            System.err.println(format + " " + priority + " [" + tag + "] " + message);
        }
    }

    @Override
    public String getName() {
        return WAVE_LOGGER;
    }

    @Override
    public boolean isLoggable(Level level) {
        return (level.compareTo(sysLevel) >= 0);
    }

    @Override
    public void log(Level level, ResourceBundle rb, String content, Throwable t) {
        if (isLoggable(level)) {
            String format = dtf.format(LocalTime.now());
            System.err.printf(format + " [%s] [WaveLogger] %s - %s%n", level, content, t);
        }
    }

    @Override
    public void log(Level level, ResourceBundle rb, String content, Object... params) {
        if (isLoggable(level)) {
            String format = dtf.format(LocalTime.now());
            String msg = content;
            if (params != null) {
                msg  = MessageFormat.format(content, params);
            }
            System.err.printf(format + " [%s] [WaveLogger] %s%n", level, msg);
        }
    }

}
