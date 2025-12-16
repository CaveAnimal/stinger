package com.codecounter.stinger.service.summary;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
