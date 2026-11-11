package com.reajason.javaweb.memshell.validation;

import com.reajason.javaweb.GenerationException;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * 注入器模板的生成期契约。{@code InjectorGenerator} 按方法名改写模板，缺失的方法不会报错，
 * 只会在目标容器里表现为壳类名或字节未被写入。生成前先把契约检查掉。
 *
 * @author ReaJason
 * @since 3.2.0
 */
public final class InjectorTemplateValidator {

    public static final String GET_CLASS_NAME = "getClassName";
    public static final String GET_BASE64_STRING = "getBase64String";
    public static final String GET_URL_PATTERN = "getUrlPattern";
    public static final String GET_HELPER_BASE64_STRING = "getHelperBase64String";

    private InjectorTemplateValidator() {
    }

    /**
     * 按类名解析模板，类加载器为空时回退 TCCL 与生成器自身加载器。
     */
    public static TypeDescription resolve(String templateClassName, ClassLoader classLoader) {
        ClassLoader effective = classLoader;
        if (effective == null) {
            effective = Thread.currentThread().getContextClassLoader();
        }
        if (effective == null) {
            effective = InjectorTemplateValidator.class.getClassLoader();
        }
        ClassFileLocator classFileLocator = ClassFileLocator.ForClassLoader.of(effective);
        return TypePool.Default.of(classFileLocator).describe(templateClassName).resolve();
    }

    /**
     * 模板必须是一个可实例化的类，并声明生成器要写入的必要方法。
     *
     * @param requiresHelper 本次生成是否携带辅助类；有时必须声明 {@code getHelperBase64String}
     */
    public static void requireInjectable(TypeDescription type, boolean requiresHelper) {
        requireConcrete(type);
        requireStringMethod(type, GET_CLASS_NAME);
        requireStringMethod(type, GET_BASE64_STRING);
        if (requiresHelper) {
            requireStringMethod(type, GET_HELPER_BASE64_STRING);
        }
        if (declaresNoArgMethod(type, GET_URL_PATTERN)) {
            requireStringMethod(type, GET_URL_PATTERN);
        }
        if (declaresNoArgMethod(type, GET_HELPER_BASE64_STRING)) {
            requireStringMethod(type, GET_HELPER_BASE64_STRING);
        }
    }

    /**
     * {@code staticInitialize} 会在 {@code <clinit>} 里调用模板的无参构造器。
     */
    public static void requireNoArgConstructor(TypeDescription type, String templateClassName) {
        if (type.getDeclaredMethods()
                .filter(ElementMatchers.isConstructor().and(ElementMatchers.takesArguments(0)))
                .isEmpty()) {
            throw new GenerationException("injector template " + templateClassName
                    + " has no no-arg constructor required by staticInitialize");
        }
    }

    public static boolean hasNoArgConstructor(TypeDescription type) {
        return !type.getDeclaredMethods()
                .filter(ElementMatchers.isConstructor().and(ElementMatchers.takesArguments(0)))
                .isEmpty();
    }

    private static void requireConcrete(TypeDescription type) {
        if (type.isInterface() || type.isAbstract()) {
            throw new GenerationException("injector template must be a concrete class: " + type.getName());
        }
    }

    private static void requireStringMethod(TypeDescription type, String methodName) {
        MethodList<?> methods = type.getDeclaredMethods()
                .filter(ElementMatchers.named(methodName).and(ElementMatchers.takesArguments(0)));
        if (methods.isEmpty()) {
            throw new GenerationException("injector template " + type.getName()
                    + " does not declare " + methodName + "()");
        }
        if (!methods.getOnly().getReturnType().asErasure().represents(String.class)) {
            throw new GenerationException("injector template " + type.getName()
                    + "." + methodName + "() must return String");
        }
    }

    private static boolean declaresNoArgMethod(TypeDescription type, String methodName) {
        return !type.getDeclaredMethods()
                .filter(ElementMatchers.named(methodName).and(ElementMatchers.takesArguments(0)))
                .isEmpty();
    }
}
