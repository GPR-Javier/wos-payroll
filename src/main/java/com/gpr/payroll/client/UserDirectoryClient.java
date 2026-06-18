package com.gpr.payroll.client;

import com.gpr.kernel.dto.UserSummaryDto;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Resolves canonical identity display data (name/email) from gpr-auth by id. Personal data is not
 * stored in WorkOS, so payslip generation resolves the employee name here.
 */
@Slf4j
@Component
public class UserDirectoryClient {

    private final RestClient restClient;

    public UserDirectoryClient(
            @Value("${gpr-auth.base-url:http://localhost:8081/api/auth}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Resolves a single user summary by identity id, or null on miss / lookup failure. */
    public UserSummaryDto getSummary(Long id) {
        if (id == null) {
            return null;
        }
        try {
            List<UserSummaryDto> summaries = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/users/summaries")
                            .queryParam("ids", id)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UserSummaryDto>>() {});
            return summaries == null || summaries.isEmpty() ? null : summaries.get(0);
        } catch (Exception e) {
            log.warn("User summary lookup failed for id {}: {}", id, e.getMessage());
            return null;
        }
    }
}
