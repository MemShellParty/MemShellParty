package com.reajason.javaweb.memshell.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.bytebuddy.description.type.TypeDescription;

/**
 * @author ReaJason
 * @since 2024/11/24
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ShellToolConfig {
    /**
     * 模板类的二进制类名；生成时以字节方式读取，不会加载到 JVM，
     * 因此使用方无需引入模板所引用的第三方 API（servlet、spring 等）
     */
    private String shellTemplateClassName;

    @JsonIgnore
    private transient TypeDescription shellTypeDescription;

    /**
     * shellClass 的类名
     */
    private String shellClassName;

    /**
     * 校验插件自己的配置字段。生成入口会在用户参数绑定完成后、创建生成器前调用一次。
     * 默认无校验；插件可覆写并抛出 {@code InvalidInputException} 报告非法输入。
     * 此处只依赖用户配置，不应依赖 shellTemplateClassName 等生成期才填充的字段。
     *
     * @since 3.1.0
     */
    public void validate() {
        // Default: no plugin-specific constraints.
    }
}