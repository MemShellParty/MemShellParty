package com.reajason.javaweb.memshell.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为 {@link ConfigField} 字段声明远程下拉选项的元数据。
 * <p>
 * 可标注在 {@code String} 字段上，只把选中项的 ID 绑定到配置；也可标注在
 * {@code Map<String, Object>} 字段上，把选中的完整对象绑定到配置。界面根据这些路径
 * 从远程响应中提取选项，后端生成时不发起远程请求。
 *
 * @author ReaJason
 * @since 3.1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RemoteOptions {

    /**
     * 启动页面 URL 中用于携带远程选项接口地址的查询参数名。
     */
    String urlQueryParam();

    /**
     * 选项数组在响应 JSON 中的路径；空串表示响应本身就是数组。
     */
    String itemsPath() default "";

    /**
     * 选项显示名在每个选项对象中的路径。
     */
    String labelPath() default "name";

    /**
     * 选项 ID 在每个选项对象中的路径；String/ID 模式必填，Map 对象模式忽略。
     */
    String valuePath() default "";

    /**
     * 是否必须选择一个远程选项。
     */
    boolean required() default false;
}
