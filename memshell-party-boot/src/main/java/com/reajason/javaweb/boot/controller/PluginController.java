package com.reajason.javaweb.boot.controller;

import com.reajason.javaweb.memshell.PluginBootstrap;
import com.reajason.javaweb.memshell.PluginDescriptor;
import com.reajason.javaweb.memshell.PluginLoadResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 外置插件只读目录：元信息在启动期由 {@link PluginBootstrap} 扫描一次，这里只读出结果。
 *
 * <p>不提供安装、卸载、启用、禁用等操作；插件的增加、替换、删除都需要重启宿主。
 * 字段由插件自行声明，仅用于展示，不能作为可信或权限判断依据。
 *
 * @author ReaJason
 * @since 3.1.0
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/api/plugins")
public class PluginController {

    @GetMapping
    public List<PluginDescriptor> plugins() {
        PluginLoadResult result = PluginBootstrap.getLastResult();
        return result == null ? Collections.<PluginDescriptor>emptyList() : result.getPlugins();
    }
}
