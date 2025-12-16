package com.codecounter.stinger.worker;

import picocli.CommandLine;

public final class H2WorkerMain {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new H2WorkerCommand()).execute(args);
        System.exit(exitCode);
    }
}
