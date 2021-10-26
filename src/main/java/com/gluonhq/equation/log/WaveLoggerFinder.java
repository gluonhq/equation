/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.log;

/**
 *
 * Loggerfinder that, if exported in the module, will cause the system loggers to
 * use our waveLogger. By default, this is *not* enabled in this module
 */
public class WaveLoggerFinder extends System.LoggerFinder {
    
    @Override
    public System.Logger getLogger(String name, Module module) {
        return new WaveLogger();
    }
    
}
