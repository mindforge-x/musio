package com.musio.cli.setup;

import com.musio.cli.process.BrowserLauncher;
import com.musio.cli.process.LocalProcessManager;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public class StartupWorkflow {
    private static final String WEB_URL = "http://127.0.0.1:18766";

    public int run() {
        List<MusicSourceOption> selectedSources = new SourceSelectionPrompt().select();
        if (selectedSources.isEmpty()) {
            System.out.println("Musio 启动已取消。");
            return 1;
        }

        System.out.println("已选择音乐源：" + selectedSources.stream()
                .map(MusicSourceOption::displayName)
                .collect(Collectors.joining(", ")));
        System.out.println();
        LocalProcessManager processManager = new LocalProcessManager();
        System.out.println("项目目录：" + processManager.root());
        boolean servicesReady = processManager.startRequiredServices();
        System.out.println();
        if (!servicesReady) {
            System.out.println("部分服务尚未 ready，请查看 .musio/run 下的日志。");
            System.out.println();
        }
        System.out.println("Backend: http://127.0.0.1:18765");
        System.out.println("Web:     " + WEB_URL);
        System.out.println();
        URI loginUri = URI.create(WEB_URL + "/?sources=" + sourceIds(selectedSources));
        System.out.println("登录页面：");
        System.out.println(loginUri);
        System.out.println();
        if (new BrowserLauncher().open(loginUri)) {
            System.out.println("已尝试在浏览器中打开登录页面。");
        } else {
            System.out.println("未能自动打开浏览器，请手动复制上面的登录页面地址。");
        }
        return 0;
    }

    private String sourceIds(List<MusicSourceOption> selectedSources) {
        return selectedSources.stream()
                .map(MusicSourceOption::id)
                .collect(Collectors.joining(","));
    }
}
