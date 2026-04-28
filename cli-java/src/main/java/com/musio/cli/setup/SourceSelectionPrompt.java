package com.musio.cli.setup;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class SourceSelectionPrompt {
    private static final int KEY_ESCAPE = 27;
    private static final int KEY_ENTER = 13;
    private static final int KEY_LINE_FEED = 10;
    private static final int KEY_SPACE = 32;
    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String BLUE = "\033[34m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String GRAY = "\033[90m";
    private static final String WHITE = "\033[97m";
    private static final String CYAN = "\033[36m";
    private static final String[] LOGO_LINES = {
            "███╗   ███╗██╗   ██╗███████╗██╗ ██████╗",
            "████╗ ████║██║   ██║██╔════╝██║██╔═══██╗",
            "██╔████╔██║██║   ██║███████╗██║██║   ██║",
            "██║╚██╔╝██║██║   ██║╚════██║██║██║   ██║",
            "██║ ╚═╝ ██║╚██████╔╝███████║██║╚██████╔╝",
            "╚═╝     ╚═╝ ╚═════╝ ╚══════╝╚═╝ ╚═════╝"
    };

    public List<MusicSourceOption> select() {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            if ("dumb".equalsIgnoreCase(terminal.getType())) {
                return List.of(MusicSourceOption.QQ_MUSIC);
            }
            return selectWithTerminal(terminal);
        } catch (IOException e) {
            System.out.println("Musio 无法打开交互式终端，默认选择 QQ Music。");
            return List.of(MusicSourceOption.QQ_MUSIC);
        }
    }

    private List<MusicSourceOption> selectWithTerminal(Terminal terminal) throws IOException {
        Attributes previousAttributes = terminal.enterRawMode();
        MusicSourceOption[] options = MusicSourceOption.values();
        Set<MusicSourceOption> selected = EnumSet.of(MusicSourceOption.QQ_MUSIC);
        int cursor = 0;
        String warning = "";

        try {
            while (true) {
                render(terminal, options, selected, cursor, warning);
                warning = "";

                int key = terminal.reader().read();
                if (key == KEY_ESCAPE) {
                    int next = terminal.reader().read(25L);
                    if (next == '[') {
                        int arrow = terminal.reader().read(25L);
                        if (arrow == 'A') {
                            cursor = move(options, cursor, -1);
                        } else if (arrow == 'B') {
                            cursor = move(options, cursor, 1);
                        }
                    }
                    continue;
                }

                if (key == 'k' || key == 'K') {
                    cursor = move(options, cursor, -1);
                    continue;
                }
                if (key == 'j' || key == 'J') {
                    cursor = move(options, cursor, 1);
                    continue;
                }
                if (key == KEY_SPACE) {
                    MusicSourceOption option = options[cursor];
                    if (option.enabled()) {
                        if (selected.contains(option)) {
                            selected.remove(option);
                        } else {
                            selected.add(option);
                        }
                    } else {
                        warning = option.displayName() + " 暂未在 MVP 中开放。";
                    }
                    continue;
                }
                if (key == KEY_ENTER || key == KEY_LINE_FEED) {
                    if (selected.isEmpty()) {
                        warning = "请至少选择一个音乐源后再继续。";
                        continue;
                    }
                    clear(terminal);
                    return orderedSelection(options, selected);
                }
                if (key == 'q' || key == 'Q') {
                    clear(terminal);
                    return List.of();
                }
            }
        } finally {
            terminal.setAttributes(previousAttributes);
            terminal.writer().print("\033[?25h");
            terminal.writer().flush();
        }
    }

    private int move(MusicSourceOption[] options, int cursor, int delta) {
        return (cursor + delta + options.length) % options.length;
    }

    private List<MusicSourceOption> orderedSelection(MusicSourceOption[] options, Set<MusicSourceOption> selected) {
        List<MusicSourceOption> result = new ArrayList<>();
        for (MusicSourceOption option : options) {
            if (selected.contains(option)) {
                result.add(option);
            }
        }
        return result;
    }

    private void render(
            Terminal terminal,
            MusicSourceOption[] options,
            Set<MusicSourceOption> selected,
            int cursor,
            String warning
    ) {
        clear(terminal);
        renderHeader(terminal);
        terminal.writer().println();
        terminal.writer().println(GRAY + "  │" + RESET);
        terminal.writer().println(BLUE + "  ◆" + RESET + " 选择音乐源");
        terminal.writer().println(GRAY + "  │" + RESET + " " + DIM
                + "空格 选择/取消   回车 确认   ↑/↓ 或 j/k 移动   q 取消" + RESET);
        terminal.writer().println();
        terminal.writer().println(GRAY + "  ├─" + RESET + " 可连接渠道 " + GRAY + "────────────────────────" + RESET);
        for (int i = 0; i < options.length; i++) {
            MusicSourceOption option = options[i];
            renderOption(terminal, option, i == cursor, selected.contains(option), i == options.length - 1);
        }
        terminal.writer().println();
        terminal.writer().println(GRAY + "  └─" + RESET + " 下一步：打开登录页面并进入播放器工作台");
        if (selected.isEmpty()) {
            terminal.writer().println(YELLOW + "     未选择音乐源" + RESET);
        } else {
            terminal.writer().println(GREEN + "     已选择：" + selectedNames(options, selected) + RESET);
        }
        if (!warning.isBlank()) {
            terminal.writer().println();
            terminal.writer().println(YELLOW + "  提示：" + warning + RESET);
        }
        terminal.writer().flush();
    }

    private void renderHeader(Terminal terminal) {
        terminal.writer().println();
        for (int i = 0; i < LOGO_LINES.length; i++) {
            String color = i < 2 ? WHITE : i < 5 ? "\033[37m" : GRAY;
            terminal.writer().println("  " + color + LOGO_LINES[i] + RESET);
        }
        terminal.writer().println("  " + CYAN + BOLD + "LOCAL MUSIC AGENT" + RESET
                + GRAY + "  ·  启动工作流" + RESET);
    }

    private void renderOption(
            Terminal terminal,
            MusicSourceOption option,
            boolean current,
            boolean selected,
            boolean last
    ) {
        String branch = last ? "  └─" : "  ├─";
        String continuation = last ? "    " : "  │ ";
        String cursorMarker = current ? BLUE + "›" + RESET : " ";
        String statusMarker = selected ? GREEN + "●" + RESET : option.enabled() ? "○" : GRAY + "○" + RESET;
        String checkbox = selected ? GREEN + "[x]" + RESET : option.enabled() ? "[ ]" : GRAY + "[ ]" + RESET;
        String title = option.enabled() ? option.displayName() : GRAY + option.displayName() + RESET;
        String description = option.enabled()
                ? DIM + option.description() + RESET
                : GRAY + option.description() + " · 暂未开放" + RESET;

        terminal.writer().printf(
                "%s %s %s %s %s%n",
                GRAY + branch + RESET,
                cursorMarker,
                statusMarker,
                checkbox,
                current ? BOLD + title + RESET : title
        );
        terminal.writer().printf(
                "%s   %s%n",
                GRAY + continuation + RESET,
                description
        );
        terminal.writer().println(GRAY + continuation + RESET);
    }

    private String selectedNames(MusicSourceOption[] options, Set<MusicSourceOption> selected) {
        List<String> names = new ArrayList<>();
        for (MusicSourceOption option : options) {
            if (selected.contains(option)) {
                names.add(option.displayName());
            }
        }
        return String.join(", ", names);
    }

    private void clear(Terminal terminal) {
        terminal.writer().print("\033[?25l");
        terminal.writer().print("\033[2J\033[H");
    }
}
