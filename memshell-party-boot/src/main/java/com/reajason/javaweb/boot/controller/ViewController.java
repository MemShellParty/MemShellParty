package com.reajason.javaweb.boot.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * @author ReaJason
 * @since 2024/12/19
 */
@Controller
@Slf4j
public class ViewController {
    /**
     * 视图名/资源路径白名单：只允许字母、数字、点、下划线、中划线和斜杠。
     * 排除 {@code $ { } | : %} 等字符，防止 Thymeleaf 表达式注入、{@code redirect:}/{@code forward:}
     * 视图前缀和 URL 编码绕过。
     */
    private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]*");

    @GetMapping("/")
    public RedirectView index() {
        RedirectView redirect = new RedirectView("/ui");
        redirect.setPropagateQueryParams(true);
        return redirect;
    }

    @GetMapping({"/api/search", "/api/search.data"})
    @ResponseBody
    public String handleSearch(HttpServletRequest request, HttpServletResponse response) {
        String fullPath = requestPath(request);
        String relativePath = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
        if (!isSafePath(relativePath)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "Invalid path";
        }
        return renderFileData(relativePath, response);
    }

    @GetMapping("/ui/**")
    @SneakyThrows
    public Object handleView(HttpServletRequest request, HttpServletResponse response) {
        String fullPath = requestPath(request);
        if ("/ui".equals(fullPath) || "/ui/".equals(fullPath)) {
            return "index";
        }
        if (!fullPath.startsWith("/ui/")) {
            return ResponseEntity.badRequest().body("Invalid path");
        }
        String docPath = fullPath.substring(4);
        if (!isSafePath(docPath)) {
            return ResponseEntity.badRequest().body("Invalid path");
        }
        if (docPath.endsWith(".data")) {
            return ResponseEntity.ok(renderFileData(docPath, response));
        }
        return docPath + "/index";
    }

    /**
     * 取出去掉 context-path 后的原始请求路径。
     * <p>
     * 不能用 {@code replace(contextPath, "")}：它会把路径中任意位置出现的 context-path 都删掉，
     * 攻击者可用 {@code $/<ctx>{...}} 之类的请求在视图名里凭空拼出 {@code ${...}}。
     * 这里只剥离开头的前缀，不做任何重写。
     */
    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private static boolean isSafePath(String path) {
        return SAFE_PATH.matcher(path).matches() && !path.contains("..");
    }

    private String renderFileData(String relativePath, HttpServletResponse response) {
        try {
            String templatePath = "templates/" + relativePath;
            ClassPathResource resource = new ClassPathResource(templatePath);
            if (!resource.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return "File not found: " + relativePath;
            }
            InputStreamReader reader = new InputStreamReader(
                    resource.getInputStream(),
                    StandardCharsets.UTF_8
            );
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return "Error reading file: " + e.getMessage();
        }
    }
}
