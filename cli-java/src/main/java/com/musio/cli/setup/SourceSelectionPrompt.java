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
        int next = cursor;
        for (int i = 0; i < options.length; i++) {
            next = (next + delta + options.length) % options.length;
            if (options[next].enabled()) {
                return next;
            }
        }
        return cursor;
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
        terminal.writer().println("Musio 启动工作流");
        terminal.writer().println();
        terminal.writer().println("请选择要连接的音乐源：");
        terminal.writer().println("  空格 = 选择/取消   回车 = 确认   上/下方向键或 j/k = 移动   q = 取消");
        terminal.writer().println();
        for (int i = 0; i < options.length; i++) {
            MusicSourceOption option = options[i];
            String pointer = i == cursor ? ">" : " ";
            String checkbox = selected.contains(option) ? "[x]" : "[ ]";
            String state = option.enabled() ? "" : "（暂未开放）";
            terminal.writer().printf(
                    "%s %s %-22s %s%s%n",
                    pointer,
                    checkbox,
                    option.displayName(),
                    option.description(),
                    state
            );
        }
        if (!warning.isBlank()) {
            terminal.writer().println();
            terminal.writer().println("提示：" + warning);
        }
        terminal.writer().flush();
    }

    private void clear(Terminal terminal) {
        terminal.writer().print("\033[?25l");
        terminal.writer().print("\033[2J\033[H");
    }
}
