package com.reajason.javaweb.vul.springboot2.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * @author ReaJason
 * @since 2024/12/22
 */
@RestController
@RequestMapping("/b64")
public class Base64ClassLoaderController extends ClassLoader {
    @PostMapping
    public String base64ClassLoader(@RequestParam(value = "data", required = false) String data,
                                    @RequestBody(required = false) String body) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(data == null ? body : data);
        // 用应用自身的类加载器 define，保证注入类能看到嵌套 jar 里的 javax.servlet；
        // Controller 本身继承 ClassLoader，直接 defineClass 会挂到系统加载器下。
        java.lang.reflect.Method defineClass = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, int.class, int.class);
        defineClass.setAccessible(true);
        Class<?> clazz = (Class<?>) defineClass.invoke(getClass().getClassLoader(), null, bytes, 0, bytes.length);
        Object o = clazz.newInstance();
        return o.toString();
    }
}
