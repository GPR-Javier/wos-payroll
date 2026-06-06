package com.gpr.payroll.config;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Prints a large "ANSI Shadow" block-letter banner spelling "&lt;SERVICE&gt; STARTED" the moment the
 * service is fully up (ApplicationReadyEvent fires AFTER seeders/CommandLineRunners finish). Written
 * to System.out so it has no log prefix and is impossible to miss. Text is rendered from the embedded
 * 6-row font, so the same file drops into any service unchanged — only the package line differs.
 *
 * <p>Background cells are filled with {@code ░} for the shaded look; set {@link #SHADE} to ' ' for a
 * plain (spaces) background instead.
 */
@Component
@RequiredArgsConstructor
public class StartupBanner {

    /** Background fill character ('░' = shaded look, ' ' = plain). */
    private static final char SHADE = '░';

    private final Environment env;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String name = env.getProperty("spring.application.name", "service").toUpperCase().replace('-', ' ');

        System.out.println();
        for (String row : render(name)) System.out.println(row.replace(' ', SHADE));
        System.out.println();
        for (String row : render("STARTED")) System.out.println(row.replace(' ', SHADE));
        System.out.println();
    }

    /** Builds the 6 rows of block text for {@code text} (unknown chars render as a blank). */
    private static String[] render(String text) {
        String[] rows = { "", "", "", "", "", "" };
        for (char c : text.toCharArray()) {
            String[] g = FONT.getOrDefault(Character.toUpperCase(c), FONT.get(' '));
            for (int i = 0; i < 6; i++) rows[i] += g[i];
        }
        return rows;
    }

    // ── ANSI Shadow font (6 rows per glyph; backgrounds are spaces, filled at print time) ──────────
    private static final Map<Character, String[]> FONT = new HashMap<>();

    private static void put(char c, String... rows) {
        FONT.put(c, rows);
    }

    static {
        put(' ', "    ", "    ", "    ", "    ", "    ", "    ");
        put('A', " █████╗ ", "██╔══██╗", "███████║", "██╔══██║", "██║  ██║", "╚═╝  ╚═╝");
        put('C', " ██████╗", "██╔════╝", "██║     ", "██║     ", "╚██████╗", " ╚═════╝");
        put('D', "██████╗ ", "██╔══██╗", "██║  ██║", "██║  ██║", "██████╔╝", "╚═════╝ ");
        put('E', "███████╗", "██╔════╝", "█████╗  ", "██╔══╝  ", "███████╗", "╚══════╝");
        put('H', "██╗  ██╗", "██║  ██║", "███████║", "██╔══██║", "██║  ██║", "╚═╝  ╚═╝");
        put('I', "██╗", "██║", "██║", "██║", "██║", "╚═╝");
        put('L', "██╗     ", "██║     ", "██║     ", "██║     ", "███████╗", "╚══════╝");
        put('N', "███╗   ██╗", "████╗  ██║", "██╔██╗ ██║", "██║╚██╗██║", "██║ ╚████║", "╚═╝  ╚═══╝");
        put('O', " ██████╗ ", "██╔═══██╗", "██║   ██║", "██║   ██║", "╚██████╔╝", " ╚═════╝ ");
        put('P', "██████╗ ", "██╔══██╗", "██████╔╝", "██╔═══╝ ", "██║     ", "╚═╝     ");
        put('R', "██████╗ ", "██╔══██╗", "██████╔╝", "██╔══██╗", "██║  ██║", "╚═╝  ╚═╝");
        put('S', "███████╗", "██╔════╝", "███████╗", "╚════██║", "███████║", "╚══════╝");
        put('T', "████████╗", "╚══██╔══╝", "   ██║   ", "   ██║   ", "   ██║   ", "   ╚═╝   ");
        put('U', "██╗   ██╗", "██║   ██║", "██║   ██║", "██║   ██║", "╚██████╔╝", " ╚═════╝ ");
        put('W', "██╗    ██╗", "██║    ██║", "██║ █╗ ██║", "██║███╗██║", "╚███╔███╔╝", " ╚══╝╚══╝ ");
        put('Y', "██╗   ██╗", "╚██╗ ██╔╝", " ╚████╔╝ ", "  ╚██╔╝  ", "   ██║   ", "   ╚═╝   ");
    }
}
