package com.brightline.integrationhealth.casestudy.service;

import com.brightline.integrationhealth.casestudy.model.IntegrationLog;
import com.brightline.integrationhealth.casestudy.model.IntegrationLogWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * Loads and parses the integration log dataset from the classpath.
 *
 * The JSON file is expected at: data/integration_logs.json
 * with the structure: { "logs": [ ... ] }
 */
@Component
public class LogReader {

    private static final Logger log = LoggerFactory.getLogger(LogReader.class);
    private static final String LOG_FILE_PATH = "data/integration_logs.json";

    private final ObjectMapper objectMapper;

    public LogReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Reads and parses all log entries from the classpath JSON resource.
     *
     * @return list of IntegrationLog records, or an empty list if the file
     *         is missing, empty, or contains no "logs" array.
     */
    public List<IntegrationLog> loadLogs() {
        try {
            ClassPathResource resource = new ClassPathResource(LOG_FILE_PATH);
            if (!resource.exists()) {
                log.warn("Log file not found on classpath: {}", LOG_FILE_PATH);
                return Collections.emptyList();
            }

            try (InputStream inputStream = resource.getInputStream()) {
                IntegrationLogWrapper wrapper = objectMapper.readValue(inputStream, IntegrationLogWrapper.class);
                if (wrapper == null || wrapper.getLogs() == null) {
                    log.warn("Log file contained no 'logs' array");
                    return Collections.emptyList();
                }
                log.info("Loaded {} integration log entries", wrapper.getLogs().size());
                return wrapper.getLogs();
            }
        } catch (IOException e) {
            log.error("Failed to read integration logs from {}: {}", LOG_FILE_PATH, e.getMessage());
            return Collections.emptyList();
        }
    }
}
