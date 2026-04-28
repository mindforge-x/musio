package com.musio.cli.process;

import com.musio.cli.config.MusioCliConfig;
import com.musio.cli.config.MusioCliConfigStore;
import com.musio.cli.ui.CliTimeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;

public class LocalProcessManager {
    private final Path root;
    private final Path runDirectory;
    private final MusioCliConfig config;
    private final HttpProbe httpProbe = new HttpProbe();

    public LocalProcessManager() {
        this(new MusioCliConfigStore().load());
    }

    public LocalProcessManager(MusioCliConfig config) {
        this.config = config;
        this.root = new ProjectRootResolver().resolve();
        this.runDirectory = root.resolve(".musio").resolve("run");
    }

    public boolean startRequiredServices() {
        createRunDirectory();
        CliTimeline.step("启动本地服务");
        boolean sidecarReady = startIfNeeded(LocalService.QQMUSIC_SIDECAR);
        boolean backendReady = startIfNeeded(LocalService.BACKEND);
        boolean frontendReady = startIfNeeded(LocalService.FRONTEND);
        return sidecarReady && backendReady && frontendReady;
    }

    public Path root() {
        return root;
    }

    public int stopServices() {
        ProcessBuilder builder = isWindows() ? windowsStopProcess() : unixStopProcess();
        builder.directory(root.toFile());
        builder.inheritIO();
        try {
            Process process = builder.start();
            return process.waitFor();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to stop Musio services from " + root, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        }
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
        Process process = launch(service);
        writePid(service, process);
        if (service == LocalService.BACKEND) {
            CliTimeline.muted("Spring 首次启动可能会下载 Maven 依赖，最长等待 "
                    + service.timeout().toSeconds() + "s");
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
        ProcessBuilder builder = isWindows() ? windowsProcess(service) : unixProcess(service);
        builder.directory(root.toFile());
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
        environment.put("MUSIO_SERVER_HOST", config.serverHost());
        environment.put("MUSIO_SERVER_PORT", Integer.toString(config.serverPort()));
        environment.put("MUSIO_WEB_HOST", config.webHost());
        environment.put("MUSIO_WEB_PORT", Integer.toString(config.webPort()));
        environment.put("MUSIO_BACKEND_BASE_URL", config.backendBaseUrl());
        environment.put("MUSIO_CORS_ALLOWED_ORIGINS", config.corsAllowedOrigins());
        environment.put("MUSIO_QQMUSIC_HOST", config.qqMusicSidecarHost());
        environment.put("MUSIO_QQMUSIC_PORT", Integer.toString(config.qqMusicSidecarPort()));
        environment.put("MUSIO_QQMUSIC_SIDECAR_BASE_URL", config.qqMusicSidecarBaseUrl());
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

    private ProcessBuilder unixStopProcess() {
        return new ProcessBuilder(
                "/bin/bash",
                root.resolve("scripts/stop-dev.sh").toString(),
                Integer.toString(config.serverPort()),
                Integer.toString(config.webPort()),
                Integer.toString(config.qqMusicSidecarPort())
        );
    }

    private ProcessBuilder windowsStopProcess() {
        return new ProcessBuilder(
                "powershell.exe",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                root.resolve("scripts\\win\\stop-windows.ps1").toString(),
                "-Ports",
                config.serverPort() + "," + config.webPort() + "," + config.qqMusicSidecarPort()
        );
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
                    runDirectory.resolve(fileStem(service) + ".pid"),
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

    private String fileStem(LocalService service) {
        return service.processName();
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }
}
