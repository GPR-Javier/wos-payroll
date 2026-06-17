package com.gpr.payroll.config;

import org.springframework.boot.ResourceBanner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Prints the {@code banner.txt} art AFTER the service is fully up, instead of Spring's default
 * print-before-startup. The default banner is disabled via {@code spring.main.banner-mode=off}; this
 * listener renders the same resource (placeholders + ANSI colours resolved by {@link ResourceBanner})
 * once {@link ApplicationReadyEvent} fires — i.e. right after the "Started …" line.
 */
@Component
public class StartupBanner {

    private final Environment environment;

    public StartupBanner(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printOnReady() {
        ResourceBanner banner = new ResourceBanner(new ClassPathResource("banner.txt"));
        banner.printBanner(environment, StartupBanner.class, System.out);
    }
}
