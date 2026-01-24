package com.ai.infrastructure.intent.action;

import com.ai.infrastructure.intent.action.annotation.Param;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.ConversionService;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

final class ActionMethodArgumentBinder {

    private final ConversionService conversionService;
    private final ObjectMapper objectMapper;

    ActionMethodArgumentBinder(ConversionService conversionService, ObjectMapper objectMapper) {
        this.conversionService = conversionService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    static boolean isContextParameter(Class<?> type) {
        return type == ActionContext.class || type == OrchestrationContext.class || type == PipelineContext.class;
    }

    Object[] bind(Method method, Map<String, Object> params, ActionContext context) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Class<?> targetType = parameter.getType();

            if (targetType == ActionContext.class) {
                args[i] = context;
                continue;
            }
            if (targetType == OrchestrationContext.class) {
                args[i] = context != null ? context.orchestrationContext() : null;
                continue;
            }
            if (targetType == PipelineContext.class) {
                args[i] = context != null ? context.pipelineContext() : null;
                continue;
            }

            Param param = parameter.getAnnotation(Param.class);
            if (param == null) {
                throw new IllegalStateException("Parameter must have @Param annotation: " + method.getDeclaringClass().getName() + "#" + method.getName());
            }

            String name = StringUtils.hasText(param.value()) ? param.value() : parameter.getName();
            Object raw = params != null ? params.get(name) : null;

            if (param.required() && isMissing(raw)) {
                throw new MissingActionParameterException(name);
            }

            Object converted = convert(raw, parameter.getParameterizedType());
            if (converted != null) {
                validate(converted, param, name);
            }
            args[i] = converted;
        }

        return args;
    }

    private boolean isMissing(Object raw) {
        if (raw == null) {
            return true;
        }
        String s = raw.toString();
        if (!StringUtils.hasText(s)) {
            return true;
        }
        String lowered = s.trim().toLowerCase(Locale.ROOT);
        return lowered.contains("required") || lowered.contains("optional") || lowered.contains("example") || lowered.contains("e.g");
    }

    private Object convert(Object raw, Type targetType) {
        if (raw == null) {
            return null;
        }
        if (targetType instanceof Class<?> clazz && clazz.isInstance(raw)) {
            return raw;
        }

        if (targetType instanceof Class<?> clazz) {
            if (clazz.isEnum()) {
                String value = raw.toString();
                @SuppressWarnings("unchecked")
                Class<? extends Enum> enumType = (Class<? extends Enum>) clazz;
                for (Enum<?> constant : enumType.getEnumConstants()) {
                    if (constant.name().equalsIgnoreCase(value)) {
                        return constant;
                    }
                }
            }
            if (conversionService != null && conversionService.canConvert(raw.getClass(), clazz)) {
                return conversionService.convert(raw, clazz);
            }
        }

        JavaType javaType = objectMapper.getTypeFactory().constructType(targetType);
        return objectMapper.convertValue(raw, javaType);
    }

    private void validate(Object value, Param param, String name) {
        String stringValue = value.toString();

        if (StringUtils.hasText(param.pattern()) && !stringValue.matches(param.pattern())) {
            throw new InvalidActionParameterException(name, "does not match expected pattern");
        }

        if (param.allowedValues().length > 0) {
            boolean allowed = Arrays.stream(param.allowedValues())
                .filter(StringUtils::hasText)
                .anyMatch(allowedValue -> allowedValue.equalsIgnoreCase(stringValue));
            if (!allowed) {
                throw new InvalidActionParameterException(name, "must be one of: " + Arrays.toString(param.allowedValues()));
            }
        }

        if (value instanceof Number number) {
            long numeric = number.longValue();
            if (param.min() != Long.MIN_VALUE && numeric < param.min()) {
                throw new InvalidActionParameterException(name, "must be >= " + param.min());
            }
            if (param.max() != Long.MAX_VALUE && numeric > param.max()) {
                throw new InvalidActionParameterException(name, "must be <= " + param.max());
            }
        }
    }

    static final class MissingActionParameterException extends RuntimeException {
        private final String paramName;

        MissingActionParameterException(String paramName) {
            super("Missing required action parameter: " + paramName);
            this.paramName = paramName;
        }

        String paramName() {
            return paramName;
        }
    }

    static final class InvalidActionParameterException extends RuntimeException {
        private final String paramName;

        InvalidActionParameterException(String paramName, String message) {
            super("Invalid action parameter '" + paramName + "': " + message);
            this.paramName = paramName;
        }

        String paramName() {
            return paramName;
        }
    }
}

