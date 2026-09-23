package io.golem.datasync.engine;

public record EngineHealth(boolean online, String version, String message) {}
