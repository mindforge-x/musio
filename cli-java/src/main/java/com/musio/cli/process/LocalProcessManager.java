package com.musio.cli.process;

import com.musio.cli.config.MusioCliConfig;
import com.musio.cli.config.MusioCliConfigStore;
import com.musio.cli.ui.CliTimeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

public class LocalProcessManager {
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

    private enum ProcessHost {
        LOCAL,
        WINDOWS
    }

    private record ProcessTarget(long pid, ProcessHost host) {
        String label() {
            return host == ProcessHost.WINDOWS ? "windows pid=" + pid : "pid=" + pid;
        }
    }

    private final Path root;
    private final Path releaseDirectory;
    private final Path runDirectory;
    private final Path runtimeDirectory;
    private final MusioCliConfig config;
    private final List<String> selectedSourceIds;
    private final boolean releaseMode;
    private final HttpProbe httpProbe = new HttpProbe();

    public LocalProcessManager() {
        this(new MusioCliConfigStore().load());
    }

    public LocalProcessManager(MusioCliConfig config) {
        this(config, List.of("qqmusic"));
    }

    public LocalProcessManager(MusioCliConfig config, List<String> selectedSourceIds) {
        this.config = config;
        this.selectedSourceIds = normalizeSourceIds(selectedSourceIds);
        this.root = new ProjectRootResolver().resolve();
        this.releaseDirectory = ProjectRootResolver.releaseDirectory(root).orElse(null);
        this.releaseMode = releaseDirectory != null;
        this.runDirectory = runDirectory(root, config, releaseMode);
        this.runtimeDirectory = musioHome(config).resolve("runtime");
    }

    public boolean startRequiredServices() {
        createRunDirectory();
        CliTimeline.step("启动本地服务");
        boolean ready = true;
        for (LocalService service : servicesToStart()) {
            ready = startIfNeeded(service) && ready;
        }
        return ready;
    }

    public boolean publishSourceContext() {
        String payload = "{\"selectedSources\":["
                + selectedSourceIds.stream()
                .map(sourceId -> "\"" + jsonEscape(sourceId) + "\"")
                .collect(Collectors.joining(","))
                + "],\"activeSource\":\""
                + jsonEscape(selectedSourceIds.getFirst())
                + "\",\"userId\":\"local\"}";
        HttpRequest request = HttpRequest.newBuilder(config.sourceContextUri())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            HttpResponse<Void> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    public Path root() {
        return root;
    }

    public Path runDirectory() {
        return runDirectory;
    }

    public String webBaseUrl() {
        return releaseMode ? config.backendBaseUrl() : config.webBaseUrl();
    }

    public int stopServices() {
        CliTimeline.step("停止本地服务");
        int failures = 0;
        Set<ProcessTarget> handledTargets = new HashSet<>();
        for (LocalService service : servicesToStop()) {
            CliTimeline.branch(service.displayName());
            if (!stopService(service, handledTargets)) {
                failures++;
            }
        }
        CliTimeline.end(failures == 0 ? "Musio 服务已停止" : "Musio 服务停止未完全成功");
        return failures == 0 ? 0 : 1;
    }

    private boolean stopService(LocalService service, Set<ProcessTarget> handledTargets) {
        boolean success = true;
        boolean found = false;
        boolean deletePidFile = false;
        Path pidPath = pidPath(service);

        Optional<Long> pid = readPid(pidPath);
        if (pid.isPresent()) {
            found = true;
            boolean stopped = stopTarget(new ProcessTarget(pid.get(), ProcessHost.LOCAL), handledTargets);
            deletePidFile = stopped;
            success = stopped && success;
        } else if (Files.isRegularFile(pidPath)) {
            CliTimeline.muted("pid 文件无效，已移除：" + pidPath);
            deletePidFile = true;
        } else {
            CliTimeline.muted("没有找到 pid 文件");
        }

        for (ProcessTarget target : listeningProcessTargets(servicePort(service))) {
            if (handledTargets.contains(target)) {
                continue;
            }
            found = true;
            CliTimeline.muted("端口 " + servicePort(service) + " 仍有监听，尝试停止 " + target.label());
            success = stopTarget(target, handledTargets) && success;
        }

        for (ProcessTarget target : knownProjectProcessTargets(service)) {
            if (handledTargets.contains(target)) {
                continue;
            }
            found = true;
            CliTimeline.muted("发现残留任务，尝试停止 " + target.label());
            success = stopTarget(target, handledTargets) && success;
        }

        List<ProcessTarget> remaining = remainingServiceTargets(service);
        if (!remaining.isEmpty()) {
            success = false;
            CliTimeline.error("仍有残留：" + remaining.stream()
                    .map(ProcessTarget::label)
                    .collect(Collectors.joining(", ")));
        } else if (found && success) {
            deletePidFile = true;
            CliTimeline.success("已清理");
        } else if (!found) {
            CliTimeline.muted("未发现运行中的任务");
        }
        if (deletePidFile) {
            deletePid(pidPath);
        }
        return success;
    }

    private Optional<Long> readPid(Path pidPath) {
        if (!Files.isRegularFile(pidPath)) {
            return Optional.empty();
        }
        try {
            String raw = Files.readString(pidPath).trim();
            if (!isPositiveInteger(raw)) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(raw));
        } catch (IOException | NumberFormatException e) {
            CliTimeline.error("读取 pid 失败：" + pidPath);
            return Optional.empty();
        }
    }

    private void deletePid(Path pidPath) {
        try {
            Files.deleteIfExists(pidPath);
        } catch (IOException e) {
            CliTimeline.muted("无法删除 pid 文件：" + pidPath);
        }
    }

    private boolean stopTarget(ProcessTarget target, Set<ProcessTarget> handledTargets) {
        if (target.pid() <= 0 || isProtectedTarget(target)) {
            return true;
        }
        if (!handledTargets.add(target)) {
            return true;
        }

        boolean stopped = switch (target.host()) {
            case LOCAL -> stopLocalPid(target.pid());
            case WINDOWS -> stopWindowsPid(target.pid());
        };
        if (stopped) {
            CliTimeline.success("已停止 " + target.label());
        } else {
            CliTimeline.error("未能停止 " + target.label());
        }
        return stopped;
    }

    private boolean stopLocalPid(long pid) {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty() || !handle.get().isAlive()) {
            return true;
        }
        return stopProcessTree(handle.get());
    }

    private boolean isProtectedTarget(ProcessTarget target) {
        return target.host() == ProcessHost.LOCAL && currentProcessFamily().contains(target.pid());
    }

    private Set<Long> currentProcessFamily() {
        Set<Long> family = new HashSet<>();
        ProcessHandle current = ProcessHandle.current();
        family.add(current.pid());
        Optional<ProcessHandle> parent = current.parent();
        while (parent.isPresent()) {
            ProcessHandle handle = parent.get();
            family.add(handle.pid());
            parent = handle.parent();
        }
        return family;
    }

    private boolean startIfNeeded(LocalService service) {
        var healthUri = service.healthUri(config);
        CliTimeline.branch(service.displayName());
        if (httpProbe.isReady(healthUri)) {
            CliTimeline.success("已在运行：" + healthUri);
            return true;
        }
        if (httpProbe.canConnect(healthUri)) {
            CliTimeline.error("端口已被其他服务占用：" + healthUri.getHost() + ":" + healthUri.getPort());
            CliTimeline.detail("可修改端口：musio config set " + service.portConfigKey() + " <port>");
            return false;
        }

        CliTimeline.pending("正在启动");
        Process process;
        try {
            process = launch(service);
        } catch (IllegalStateException e) {
            CliTimeline.error(e.getMessage());
            return false;
        }
        writePid(service, process);
        if (!releaseMode && service == LocalService.BACKEND) {
            CliTimeline.muted("Spring 首次启动可能会下载 Maven 依赖，最长等待 "
                    + service.timeout().toSeconds() + "s");
        } else if (releaseMode && service == LocalService.QQMUSIC_SIDECAR) {
            CliTimeline.muted("使用发布包内置 QQMusic sidecar");
        }

        if (httpProbe.waitUntilReady(healthUri, service.timeout())) {
            CliTimeline.success("ready: " + healthUri);
            return true;
        } else {
            if (!process.isAlive()) {
                CliTimeline.error("进程在 ready 前退出，exit code: " + process.exitValue());
            }
            CliTimeline.error("未在 " + service.timeout().toSeconds() + "s 内 ready");
            CliTimeline.detail("日志：" + logPath(service));
            return false;
        }
    }

    private Process launch(LocalService service) {
        ProcessBuilder builder = releaseMode ? releaseProcess(service) : devProcess(service);
        if (builder.directory() == null) {
            builder.directory(root.toFile());
        }
        configureEnvironment(builder.environment());
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath(service).toFile()));
        builder.redirectErrorStream(true);
        try {
            return builder.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start " + service.displayName() + " from " + root, e);
        }
    }

    private void configureEnvironment(Map<String, String> environment) {
        environment.put("MUSIO_CONFIG", config.configPath().toString());
        environment.put("MUSIO_HOME", config.storageHome().toString());
        environment.put("MUSIO_STORAGE_HOME", config.storageHome().toString());
        environment.put("MUSIO_RUNTIME_MODE", releaseMode ? "release" : "dev");
        environment.put("MUSIO_SERVER_HOST", config.serverHost());
        environment.put("MUSIO_SERVER_PORT", Integer.toString(config.serverPort()));
        environment.put("MUSIO_WEB_HOST", config.webHost());
        environment.put("MUSIO_WEB_PORT", Integer.toString(config.webPort()));
        environment.put("MUSIO_BACKEND_BASE_URL", config.backendBaseUrl());
        environment.put("MUSIO_CORS_ALLOWED_ORIGINS", corsAllowedOrigins());
        environment.put("MUSIO_BACKEND_LOG_FILE", logPath(LocalService.BACKEND).toString());
        environment.put("MUSIO_QQMUSIC_HOST", config.qqMusicSidecarHost());
        environment.put("MUSIO_QQMUSIC_PORT", Integer.toString(config.qqMusicSidecarPort()));
        environment.put("MUSIO_QQMUSIC_SIDECAR_BASE_URL", config.qqMusicSidecarBaseUrl());
        environment.put("MUSIO_SELECTED_SOURCES", String.join(",", selectedSourceIds));
        environment.put("MUSIO_ACTIVE_SOURCE", selectedSourceIds.getFirst());
    }

    private ProcessBuilder devProcess(LocalService service) {
        return isWindows() ? windowsProcess(service) : unixProcess(service);
    }

    private ProcessBuilder releaseProcess(LocalService service) {
        ProcessBuilder builder = switch (service) {
            case QQMUSIC_SIDECAR -> releaseSidecarProcess();
            case BACKEND -> new ProcessBuilder(releaseJavaExecutable(), "-jar", releaseBackendJar().toString());
            case FRONTEND -> throw new IllegalStateException("生产模式不再启动独立 React frontend");
        };
        return detachedLinuxProcess(builder);
    }

    private ProcessBuilder detachedLinuxProcess(ProcessBuilder builder) {
        if (!isLinux()) {
            return builder;
        }

        List<String> command = new ArrayList<>();
        command.add("setsid");
        command.addAll(builder.command());

        ProcessBuilder detached = new ProcessBuilder(command);
        if (builder.directory() != null) {
            detached.directory(builder.directory());
        }
        detached.redirectInput(ProcessBuilder.Redirect.from(Path.of("/dev/null").toFile()));
        return detached;
    }

    private ProcessBuilder releaseSidecarProcess() {
        Path sidecarBinary = releaseSidecarBinary();
        if (Files.isRegularFile(sidecarBinary)) {
            return new ProcessBuilder(sidecarBinary.toString())
                    .directory(sidecarBinary.getParent().toFile());
        }

        Path sidecarDirectory = releaseSidecarSourceDirectory();
        if (!Files.isDirectory(sidecarDirectory)) {
            throw new IllegalStateException("未找到 QQMusic sidecar 发布产物：" + releaseDirectory.resolve("sidecar"));
        }
        Path python = prepareReleaseSidecarPython(sidecarDirectory);
        return new ProcessBuilder(python.toString(), "-m", "app.main")
                .directory(sidecarDirectory.toFile());
    }

    private ProcessBuilder unixProcess(LocalService service) {
        if (!isLinux()) {
            return new ProcessBuilder("/bin/bash", root.resolve(service.unixScript()).toString());
        }
        ProcessBuilder builder = new ProcessBuilder(
                "setsid",
                "/bin/bash",
                root.resolve(service.unixScript()).toString()
        );
        builder.redirectInput(ProcessBuilder.Redirect.from(Path.of("/dev/null").toFile()));
        return builder;
    }

    private ProcessBuilder windowsProcess(LocalService service) {
        String script = root.resolve(service.windowsScript()).toString();
        if (service == LocalService.FRONTEND) {
            return new ProcessBuilder(
                    "powershell.exe",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    script,
                    "-NoBrowser"
            );
        }
        return new ProcessBuilder(
                "powershell.exe",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                script
        );
    }

    private Path prepareReleaseSidecarPython(Path sidecarDirectory) {
        Path venvPython = sidecarVenvPython();
        if (Files.isRegularFile(venvPython)) {
            return venvPython;
        }
        try {
            Files.createDirectories(runtimeDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 Musio runtime 目录：" + runtimeDirectory, e);
        }

        List<String> pythonCommand = findPythonCommand()
                .orElseThrow(() -> new IllegalStateException(
                        "未找到 Python 3.11+。请安装 Python 3.11+，或设置 MUSIO_PYTHON_EXE。"));

        CliTimeline.muted("创建 Python venv: " + sidecarVenvDirectory());
        runSetupCommand(append(pythonCommand, "-m", "venv", sidecarVenvDirectory().toString()), sidecarDirectory);
        CliTimeline.muted("安装 QQMusic sidecar 依赖");
        runSetupCommand(
                List.of(
                        venvPython.toString(),
                        "-m",
                        "pip",
                        "install",
                        "-r",
                        sidecarDirectory.resolve("requirements.txt").toString()
                ),
                sidecarDirectory
        );
        return venvPython;
    }

    private void runSetupCommand(List<String> command, Path directory) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(directory.toFile());
        configureEnvironment(builder.environment());
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath(LocalService.QQMUSIC_SIDECAR).toFile()));
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("准备 QQMusic sidecar 运行环境失败，日志：" + logPath(LocalService.QQMUSIC_SIDECAR));
            }
        } catch (IOException e) {
            throw new IllegalStateException("启动 QQMusic sidecar 环境准备命令失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("QQMusic sidecar 环境准备被中断", e);
        }
    }

    private void createRunDirectory() {
        try {
            Files.createDirectories(runDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create Musio run directory: " + runDirectory, e);
        }
    }

    private void writePid(LocalService service, Process process) {
        try {
            Files.writeString(
                    pidPath(service),
                    Long.toString(process.pid()),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write pid file for " + service.displayName(), e);
        }
    }

    private Path logPath(LocalService service) {
        return runDirectory.resolve(fileStem(service) + ".log");
    }

    private Path pidPath(LocalService service) {
        return runDirectory.resolve(fileStem(service) + ".pid");
    }

    private String fileStem(LocalService service) {
        return service.processName();
    }

    private List<LocalService> servicesToStart() {
        if (releaseMode) {
            return List.of(LocalService.QQMUSIC_SIDECAR, LocalService.BACKEND);
        }
        return List.of(LocalService.QQMUSIC_SIDECAR, LocalService.BACKEND, LocalService.FRONTEND);
    }

    private List<LocalService> servicesToStop() {
        if (releaseMode) {
            return List.of(LocalService.BACKEND, LocalService.QQMUSIC_SIDECAR);
        }
        return List.of(LocalService.FRONTEND, LocalService.BACKEND, LocalService.QQMUSIC_SIDECAR);
    }

    private int servicePort(LocalService service) {
        return service.healthUri(config).getPort();
    }

    private List<ProcessTarget> remainingServiceTargets(LocalService service) {
        LinkedHashSet<ProcessTarget> remaining = new LinkedHashSet<>();
        for (ProcessTarget target : listeningProcessTargets(servicePort(service))) {
            if (isTargetAlive(target) && !isProtectedTarget(target)) {
                remaining.add(target);
            }
        }
        for (ProcessTarget target : knownProjectProcessTargets(service)) {
            if (isTargetAlive(target) && !isProtectedTarget(target)) {
                remaining.add(target);
            }
        }
        return List.copyOf(remaining);
    }

    private boolean isTargetAlive(ProcessTarget target) {
        if (target.host() == ProcessHost.WINDOWS) {
            return isWindowsPidAlive(target.pid());
        }
        return ProcessHandle.of(target.pid())
                .map(ProcessHandle::isAlive)
                .orElse(false);
    }

    private List<ProcessTarget> listeningProcessTargets(int port) {
        if (port <= 0) {
            return List.of();
        }
        LinkedHashSet<ProcessTarget> targets = new LinkedHashSet<>();
        if (isWindows()) {
            windowsListeningPids(port).stream()
                    .map(pid -> new ProcessTarget(pid, ProcessHost.LOCAL))
                    .forEach(targets::add);
        } else {
            localListeningPids(port).stream()
                    .map(pid -> new ProcessTarget(pid, ProcessHost.LOCAL))
                    .forEach(targets::add);
            if (isWsl()) {
                windowsListeningPids(port).stream()
                        .map(pid -> new ProcessTarget(pid, ProcessHost.WINDOWS))
                        .forEach(targets::add);
            }
        }
        return List.copyOf(targets);
    }

    private List<Long> localListeningPids(int port) {
        if (isLinux()) {
            List<Long> pids = linuxListeningPids(port);
            if (!pids.isEmpty()) {
                return pids;
            }
        }
        return commandPids(List.of("lsof", "-nP", "-tiTCP:" + port, "-sTCP:LISTEN"), STOP_TIMEOUT);
    }

    private List<Long> linuxListeningPids(int port) {
        Set<String> inodes = linuxSocketInodesForPort(port);
        if (inodes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> pids = new LinkedHashSet<>();
        Path proc = Path.of("/proc");
        try (DirectoryStream<Path> processes = Files.newDirectoryStream(proc, Files::isDirectory)) {
            for (Path process : processes) {
                String fileName = process.getFileName().toString();
                if (!isPositiveInteger(fileName)) {
                    continue;
                }
                Path fdDirectory = process.resolve("fd");
                if (!Files.isDirectory(fdDirectory)) {
                    continue;
                }
                try (DirectoryStream<Path> fds = Files.newDirectoryStream(fdDirectory)) {
                    for (Path fd : fds) {
                        String link = Files.readSymbolicLink(fd).toString();
                        if (link.startsWith("socket:[")
                                && link.endsWith("]")
                                && inodes.contains(link.substring("socket:[".length(), link.length() - 1))) {
                            pids.add(Long.parseLong(fileName));
                            break;
                        }
                    }
                } catch (IOException | RuntimeException ignored) {
                    // Some /proc entries disappear while scanning.
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return List.copyOf(pids);
    }

    private Set<String> linuxSocketInodesForPort(int port) {
        LinkedHashSet<String> inodes = new LinkedHashSet<>();
        String expectedPort = String.format(Locale.ROOT, "%04X", port);
        for (Path path : List.of(Path.of("/proc/net/tcp"), Path.of("/proc/net/tcp6"))) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                List<String> lines = Files.readAllLines(path);
                for (int i = 1; i < lines.size(); i++) {
                    String[] fields = lines.get(i).trim().split("\\s+");
                    if (fields.length <= 9 || !"0A".equals(fields[3])) {
                        continue;
                    }
                    String localAddress = fields[1];
                    int separator = localAddress.lastIndexOf(':');
                    if (separator >= 0 && expectedPort.equalsIgnoreCase(localAddress.substring(separator + 1))) {
                        inodes.add(fields[9]);
                    }
                }
            } catch (IOException ignored) {
                // Fall back to other sources when /proc is not readable.
            }
        }
        return inodes;
    }

    private List<Long> windowsListeningPids(int port) {
        String command = "$ErrorActionPreference='SilentlyContinue'; "
                + "Get-NetTCPConnection -LocalPort " + port + " -State Listen "
                + "| Select-Object -ExpandProperty OwningProcess | Sort-Object -Unique";
        return commandPids(List.of(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                command
        ), STOP_TIMEOUT);
    }

    private List<ProcessTarget> knownProjectProcessTargets(LocalService service) {
        LinkedHashSet<ProcessTarget> targets = new LinkedHashSet<>();
        knownLocalProjectPids(service).stream()
                .map(pid -> new ProcessTarget(pid, ProcessHost.LOCAL))
                .forEach(targets::add);

        if (isWindows()) {
            knownWindowsProjectPids(service).stream()
                    .map(pid -> new ProcessTarget(pid, ProcessHost.LOCAL))
                    .forEach(targets::add);
        } else if (isWsl()) {
            knownWindowsProjectPids(service).stream()
                    .map(pid -> new ProcessTarget(pid, ProcessHost.WINDOWS))
                    .forEach(targets::add);
        }
        return List.copyOf(targets);
    }

    private List<Long> knownLocalProjectPids(LocalService service) {
        Set<Long> protectedPids = currentProcessFamily();
        List<String> rootNeedles = rootCommandLineNeedles();
        LinkedHashSet<Long> pids = new LinkedHashSet<>();
        ProcessHandle.allProcesses()
                .filter(ProcessHandle::isAlive)
                .filter(handle -> !protectedPids.contains(handle.pid()))
                .forEach(handle -> {
                    String commandLine = commandLine(handle).orElse("");
                    if (isKnownServiceProcess(service, commandLine)
                            && (containsAnyNormalized(commandLine, rootNeedles)
                            || processWorkingDirectoryUnderRoot(handle))) {
                        pids.add(handle.pid());
                    }
                });
        return List.copyOf(pids);
    }

    private List<Long> knownWindowsProjectPids(LocalService service) {
        Optional<String> windowsRoot = windowsRootPath();
        if (windowsRoot.isEmpty()) {
            return List.of();
        }
        String command = "$rootPattern=[regex]::Escape('" + powershellSingleQuoted(windowsRoot.get()) + "'); "
                + "$servicePattern='" + powershellSingleQuoted(windowsServicePattern(service)) + "'; "
                + "Get-CimInstance Win32_Process -ErrorAction SilentlyContinue "
                + "| Where-Object { $_.CommandLine -and $_.CommandLine -match $rootPattern "
                + "-and $_.CommandLine -match $servicePattern } "
                + "| Select-Object -ExpandProperty ProcessId";
        return commandPids(List.of(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                command
        ), STOP_TIMEOUT);
    }

    private Optional<String> commandLine(ProcessHandle handle) {
        ProcessHandle.Info info = handle.info();
        return info.commandLine().or(info::command);
    }

    private List<String> rootCommandLineNeedles() {
        List<String> needles = new ArrayList<>();
        needles.add(normalizeCommandText(root.toAbsolutePath().normalize().toString()));
        windowsRootPath().map(this::normalizeCommandText).ifPresent(needles::add);
        return needles.stream().distinct().toList();
    }

    private Optional<String> windowsRootPath() {
        if (isWindows()) {
            return Optional.of(root.toAbsolutePath().normalize().toString());
        }
        if (!isWsl()) {
            return Optional.empty();
        }
        return runCommandLines(List.of("wslpath", "-w", root.toAbsolutePath().normalize().toString()), STOP_TIMEOUT)
                .stream()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst();
    }

    private boolean containsAnyNormalized(String haystack, List<String> needles) {
        String normalized = normalizeCommandText(haystack);
        return needles.stream().anyMatch(normalized::contains);
    }

    private String normalizeCommandText(String value) {
        return value.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private boolean processWorkingDirectoryUnderRoot(ProcessHandle handle) {
        if (!isLinux()) {
            return false;
        }
        try {
            Path cwd = Files.readSymbolicLink(Path.of("/proc", Long.toString(handle.pid()), "cwd"))
                    .toAbsolutePath()
                    .normalize();
            return cwd.startsWith(root.toAbsolutePath().normalize());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private boolean isKnownServiceProcess(LocalService service, String commandLine) {
        String command = commandLine.toLowerCase(Locale.ROOT);
        return switch (service) {
            case QQMUSIC_SIDECAR -> command.contains("app.main")
                    || command.contains("qqmusic-sidecar");
            case BACKEND -> command.contains("spring-boot:run")
                    || command.contains("backend-spring.jar")
                    || command.contains("com.musio.musioapplication");
            case FRONTEND -> command.contains("vite")
                    || command.contains("npm run dev")
                    || command.contains("npm.cmd run dev");
        };
    }

    private String windowsServicePattern(LocalService service) {
        return switch (service) {
            case QQMUSIC_SIDECAR -> "app\\.main|qqmusic-sidecar";
            case BACKEND -> "spring-boot:run|backend-spring\\.jar|com\\.musio\\.MusioApplication";
            case FRONTEND -> "vite|npm(\\.cmd)? run dev";
        };
    }

    private String powershellSingleQuoted(String value) {
        return value.replace("'", "''");
    }

    private boolean stopProcessTree(ProcessHandle handle) {
        if (isWindows()) {
            stopWindowsPid(handle.pid());
            return waitForExit(handle, List.of());
        }

        List<ProcessHandle> descendants = descendants(handle);
        signalUnixProcessGroupIfSafe(handle.pid(), "TERM");
        terminateProcesses(descendants, false);
        handle.destroy();
        if (waitForExit(handle, descendants)) {
            return true;
        }

        descendants = descendants(handle);
        signalUnixProcessGroupIfSafe(handle.pid(), "KILL");
        terminateProcesses(descendants, true);
        handle.destroyForcibly();
        return waitForExit(handle, descendants);
    }

    private List<ProcessHandle> descendants(ProcessHandle handle) {
        List<ProcessHandle> descendants = new ArrayList<>(handle.descendants().toList());
        Collections.reverse(descendants);
        return descendants;
    }

    private void terminateProcesses(List<ProcessHandle> handles, boolean forcibly) {
        for (ProcessHandle process : handles) {
            if (!process.isAlive()) {
                continue;
            }
            if (forcibly) {
                process.destroyForcibly();
            } else {
                process.destroy();
            }
        }
    }

    private boolean waitForExit(ProcessHandle handle, List<ProcessHandle> descendants) {
        long deadline = System.nanoTime() + STOP_TIMEOUT.toNanos();
        waitForProcess(handle, deadline);
        for (ProcessHandle descendant : descendants) {
            waitForProcess(descendant, deadline);
        }
        return !handle.isAlive() && descendants.stream().noneMatch(ProcessHandle::isAlive);
    }

    private void waitForProcess(ProcessHandle handle, long deadlineNanos) {
        if (!handle.isAlive()) {
            return;
        }
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            return;
        }
        try {
            handle.onExit().get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (Exception ignored) {
            // The caller checks liveness after the wait budget is exhausted.
        }
    }

    private void signalUnixProcessGroupIfSafe(long pid, String signal) {
        if (pid <= 0 || isWindows()) {
            return;
        }
        Optional<Long> processGroupId = processGroupId(pid);
        if (processGroupId.isEmpty() || processGroupId.get() != pid) {
            return;
        }
        Optional<Long> currentProcessGroupId = processGroupId(ProcessHandle.current().pid());
        if (currentProcessGroupId.isPresent() && currentProcessGroupId.get().equals(processGroupId.get())) {
            return;
        }
        runCommandExitCode(List.of("kill", "-" + signal, "-" + pid), Duration.ofSeconds(2));
    }

    private Optional<Long> processGroupId(long pid) {
        if (pid <= 0) {
            return Optional.empty();
        }
        if (isLinux()) {
            Optional<Long> linuxProcessGroupId = linuxProcessGroupId(pid);
            if (linuxProcessGroupId.isPresent()) {
                return linuxProcessGroupId;
            }
        }
        return runCommandLines(List.of("ps", "-o", "pgid=", "-p", Long.toString(pid)), Duration.ofSeconds(2))
                .stream()
                .map(String::trim)
                .filter(LocalProcessManager::isPositiveInteger)
                .map(Long::parseLong)
                .findFirst();
    }

    private Optional<Long> linuxProcessGroupId(long pid) {
        Path statPath = Path.of("/proc", Long.toString(pid), "stat");
        if (!Files.isRegularFile(statPath)) {
            return Optional.empty();
        }
        try {
            String stat = Files.readString(statPath);
            int commandEnd = stat.lastIndexOf(')');
            if (commandEnd < 0 || commandEnd + 1 >= stat.length()) {
                return Optional.empty();
            }
            String[] fields = stat.substring(commandEnd + 1).trim().split("\\s+");
            if (fields.length < 3 || !isPositiveInteger(fields[2])) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(fields[2]));
        } catch (IOException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private boolean stopWindowsPid(long pid) {
        if (pid <= 0 || !isWindowsPidAlive(pid)) {
            return true;
        }
        int exitCode = runCommandExitCode(
                List.of("taskkill.exe", "/PID", Long.toString(pid), "/T", "/F"),
                Duration.ofSeconds(10)
        );
        if (exitCode != 0 && isWindowsPidAlive(pid)) {
            runCommandExitCode(List.of(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    windowsStopTreeCommand(pid)
            ), Duration.ofSeconds(10));
        }
        return !isWindowsPidAlive(pid);
    }

    private String windowsStopTreeCommand(long pid) {
        return "function Stop-Tree([int]$ProcessId) { "
                + "Get-CimInstance Win32_Process -Filter \"ParentProcessId = $ProcessId\" "
                + "-ErrorAction SilentlyContinue | ForEach-Object { Stop-Tree ([int]$_.ProcessId) }; "
                + "Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue "
                + "}; Stop-Tree " + pid;
    }

    private boolean isWindowsPidAlive(long pid) {
        if (pid <= 0) {
            return false;
        }
        if (isWindows()) {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        }
        int exitCode = runCommandExitCode(List.of(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                "if (Get-Process -Id " + pid + " -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
        ), Duration.ofSeconds(3));
        return exitCode == 0;
    }

    private List<Long> commandPids(List<String> command, Duration timeout) {
        return runCommandLines(command, timeout).stream()
                .map(String::trim)
                .filter(LocalProcessManager::isPositiveInteger)
                .map(Long::parseLong)
                .distinct()
                .toList();
    }

    private List<String> runCommandLines(List<String> command, Duration timeout) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = builder.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return List.of();
            }
            if (process.exitValue() != 0) {
                return List.of();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return output.lines().toList();
        } catch (IOException e) {
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private int runCommandExitCode(List<String> command, Duration timeout) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = builder.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return -1;
            }
            return process.exitValue();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private static boolean isPositiveInteger(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isWsl() {
        if (!isLinux()) {
            return false;
        }
        if (System.getenv("WSL_DISTRO_NAME") != null) {
            return true;
        }
        try {
            String version = Files.readString(Path.of("/proc/version")).toLowerCase(Locale.ROOT);
            return version.contains("microsoft") || version.contains("wsl");
        } catch (IOException e) {
            return false;
        }
    }

    private String corsAllowedOrigins() {
        if (!releaseMode) {
            return config.corsAllowedOrigins();
        }
        return config.backendBaseUrl() + "," + config.corsAllowedOrigins();
    }

    private Path releaseBackendJar() {
        return releaseDirectory.resolve("app").resolve("backend-spring.jar");
    }

    private Path releaseSidecarBinary() {
        String executable = isWindows() ? "qqmusic-sidecar.exe" : "qqmusic-sidecar";
        return releaseDirectory.resolve("sidecar").resolve(executable);
    }

    private Path releaseSidecarSourceDirectory() {
        return releaseDirectory.resolve("providers").resolve("qqmusic-python-sidecar");
    }

    private Path sidecarVenvDirectory() {
        return runtimeDirectory.resolve("qqmusic-python-sidecar-venv");
    }

    private Path sidecarVenvPython() {
        Path venv = sidecarVenvDirectory();
        if (isWindows()) {
            return venv.resolve("Scripts").resolve("python.exe");
        }
        return venv.resolve("bin").resolve("python");
    }

    private Optional<List<String>> findPythonCommand() {
        List<List<String>> candidates = new ArrayList<>();
        String configured = System.getenv("MUSIO_PYTHON_EXE");
        if (configured != null && !configured.isBlank()) {
            candidates.add(List.of(configured));
        }
        if (isWindows()) {
            candidates.add(List.of("py", "-3.11"));
            candidates.add(List.of("python"));
            candidates.add(List.of("python3"));
        } else {
            candidates.add(List.of("python3"));
            candidates.add(List.of("python"));
        }
        return candidates.stream()
                .filter(this::isPython311OrNewer)
                .findFirst();
    }

    private boolean isPython311OrNewer(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(append(
                command,
                "-c",
                "import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)"
        ));
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            return builder.start().waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private List<String> append(List<String> command, String... args) {
        List<String> result = new ArrayList<>(command);
        result.addAll(List.of(args));
        return result;
    }

    private String javaExecutable() {
        String executable = isWindows() ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private String releaseJavaExecutable() {
        String executable = isWindows() ? "java.exe" : "java";
        Path bundledJava = releaseDirectory.resolve("runtime").resolve("bin").resolve(executable);
        if (Files.isRegularFile(bundledJava)) {
            return bundledJava.toString();
        }
        return javaExecutable();
    }

    private static Path runDirectory(Path root, MusioCliConfig config, boolean releaseMode) {
        if (releaseMode) {
            return musioHome(config).resolve("run");
        }
        return root.resolve(".musio").resolve("run");
    }

    private static Path musioHome(MusioCliConfig config) {
        return config.storageHome();
    }

    private static List<String> normalizeSourceIds(List<String> sourceIds) {
        List<String> normalized = sourceIds == null ? List.of() : sourceIds.stream()
                .map(sourceId -> sourceId == null ? "" : sourceId.strip().toLowerCase(Locale.ROOT))
                .filter(sourceId -> !sourceId.isBlank())
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of("qqmusic") : normalized;
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }
}
