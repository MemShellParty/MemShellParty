package com.reajason.javaweb.boot.mcp;

public record RestCallPlan<T>(String method, String endpoint, T body) {
}
