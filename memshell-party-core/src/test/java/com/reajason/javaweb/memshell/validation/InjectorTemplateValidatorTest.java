package com.reajason.javaweb.memshell.validation;

import com.reajason.javaweb.GenerationException;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注入器模板的生成期契约：缺失的方法以前会被静默跳过，只在目标容器里表现为壳类名或字节未写入。
 *
 * @author ReaJason
 */
class InjectorTemplateValidatorTest {

    @Test
    void acceptsTemplateWithRequiredMethodsAndNoArgConstructor() {
        TypeDescription type = resolve(ValidTemplate.class);

        InjectorTemplateValidator.requireInjectable(type, true);
        InjectorTemplateValidator.requireNoArgConstructor(type, type.getName());

        assertTrue(InjectorTemplateValidator.hasNoArgConstructor(type));
    }

    @Test
    void rejectsMissingBase64Method() {
        TypeDescription type = resolve(MissingBase64Template.class);

        GenerationException error = assertThrows(GenerationException.class,
                () -> InjectorTemplateValidator.requireInjectable(type, false));

        assertTrue(error.getMessage().contains("getBase64String"), error.getMessage());
    }

    @Test
    void reportsTemplateWithoutNoArgConstructor() {
        TypeDescription type = resolve(OnlyParameterizedConstructorTemplate.class);

        assertFalse(InjectorTemplateValidator.hasNoArgConstructor(type));
        GenerationException error = assertThrows(GenerationException.class,
                () -> InjectorTemplateValidator.requireNoArgConstructor(type, type.getName()));

        assertTrue(error.getMessage().contains("no-arg constructor"), error.getMessage());
    }

    @Test
    void rejectsWrongReturnType() {
        TypeDescription type = resolve(WrongReturnTypeTemplate.class);

        assertThrows(GenerationException.class,
                () -> InjectorTemplateValidator.requireInjectable(type, false));
    }

    private static TypeDescription resolve(Class<?> type) {
        return InjectorTemplateValidator.resolve(type.getName(),
                InjectorTemplateValidatorTest.class.getClassLoader());
    }

    public static class ValidTemplate {
        public String getClassName() {
            return "";
        }

        public String getBase64String() {
            return "";
        }

        public String getHelperBase64String() {
            return "";
        }

        public String getUrlPattern() {
            return "/*";
        }
    }

    public static class MissingBase64Template {
        public String getClassName() {
            return "";
        }
    }

    public static class OnlyParameterizedConstructorTemplate {
        public OnlyParameterizedConstructorTemplate(String value) {
        }

        public String getClassName() {
            return "";
        }

        public String getBase64String() {
            return "";
        }
    }

    public static class WrongReturnTypeTemplate {
        public Object getClassName() {
            return "";
        }

        public String getBase64String() {
            return "";
        }
    }
}
