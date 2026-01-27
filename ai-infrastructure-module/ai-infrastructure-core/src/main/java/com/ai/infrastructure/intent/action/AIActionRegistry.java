package com.ai.infrastructure.intent.action;

import com.ai.infrastructure.intent.action.annotation.AIAction;
import com.ai.infrastructure.intent.action.annotation.ActionAllowed;
import com.ai.infrastructure.intent.action.annotation.ActionConfirmation;
import com.ai.infrastructure.intent.action.annotation.ActionExecute;
import com.ai.infrastructure.intent.action.annotation.ActionFacts;
import com.ai.infrastructure.intent.action.annotation.Param;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Discovers {@link AIAction} beans and exposes lookup utilities by action name.
 *
 * <p>Greenfield: the framework exclusively supports annotation-driven actions.</p>
 */
@Slf4j
@Service
public class AIActionRegistry {

    private final ApplicationContext applicationContext;
    private final ObjectProvider<ConversionService> conversionServiceProvider;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;

    private Map<String, AIActionHandler> handlerByActionName = Collections.emptyMap();
    private Map<String, AIActionMetaData> metadataByActionName = Collections.emptyMap();

    public AIActionRegistry(ApplicationContext applicationContext,
                            ObjectProvider<ConversionService> conversionServiceProvider,
                            ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.applicationContext = applicationContext;
        this.conversionServiceProvider = conversionServiceProvider;
        this.objectMapperProvider = objectMapperProvider;
    }

    @PostConstruct
    void initialize() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(AIAction.class);
        if (beans == null || beans.isEmpty()) {
            handlerByActionName = Map.of();
            metadataByActionName = Map.of();
            log.info("AIActionRegistry initialized with 0 action(s)");
            return;
        }

        ActionMethodArgumentBinder binder = new ActionMethodArgumentBinder(
            conversionServiceProvider != null ? conversionServiceProvider.getIfAvailable() : null,
            objectMapperProvider != null ? objectMapperProvider.getIfAvailable(ObjectMapper::new) : new ObjectMapper()
        );

        Map<String, AIActionHandler> handlerMap = new LinkedHashMap<>();
        Map<String, AIActionMetaData> metadataMap = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            if (bean == null) {
                continue;
            }

            Class<?> targetClass = AopUtils.getTargetClass(bean);
            AIAction action = AnnotatedElementUtils.findMergedAnnotation(targetClass, AIAction.class);
            if (action == null || !StringUtils.hasText(action.name())) {
                continue;
            }

            Method executeMethod = findSingleMethod(targetClass, ActionExecute.class, "@ActionExecute");
            Method allowedMethod = findOptionalSingleMethod(targetClass, ActionAllowed.class, "@ActionAllowed");
            Method confirmationMethod = findOptionalSingleMethod(targetClass, ActionConfirmation.class, "@ActionConfirmation");
            Method factsMethod = findOptionalSingleMethod(targetClass, ActionFacts.class, "@ActionFacts");

            AIActionMetaData meta = buildMetadata(action, executeMethod);
            String key = normalize(meta.getName());
            if (handlerMap.containsKey(key)) {
                throw new IllegalStateException("Duplicate AI action name detected: '" + meta.getName() + "'");
            }

            AIActionHandler handler = new AnnotatedAIActionHandler(bean, action, meta, executeMethod, allowedMethod, confirmationMethod, factsMethod, binder);
            handlerMap.put(key, handler);
            metadataMap.put(key, meta);
        }

        handlerByActionName = Collections.unmodifiableMap(handlerMap);
        metadataByActionName = Collections.unmodifiableMap(metadataMap);
        log.info("AIActionRegistry initialized with {} action(s)", handlerByActionName.size());
    }

    public Optional<AIActionHandler> findHandler(String actionName) {
        if (!StringUtils.hasText(actionName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlerByActionName.get(normalize(actionName)));
    }

    public Optional<AIActionMetaData> findMetadata(String actionName) {
        if (!StringUtils.hasText(actionName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(metadataByActionName.get(normalize(actionName)));
    }

    public java.util.List<AIActionMetaData> getAllMetadata() {
        return java.util.List.copyOf(metadataByActionName.values());
    }

    public Map<String, AIActionHandler> getHandlerMap() {
        return handlerByActionName;
    }

    private Method findSingleMethod(Class<?> targetClass, Class<? extends Annotation> annotationType, String label) {
        Map<Method, ?> selected = MethodIntrospector.selectMethods(
            targetClass,
            (MethodIntrospector.MetadataLookup<Annotation>) method ->
                AnnotatedElementUtils.findMergedAnnotation(method, annotationType)
        );
        if (selected.isEmpty()) {
            throw new IllegalStateException("AIAction class must declare exactly one " + label + " method: " + targetClass.getName());
        }
        if (selected.size() > 1) {
            throw new IllegalStateException("AIAction class must declare exactly one " + label + " method (found " + selected.size() + "): " + targetClass.getName());
        }
        return selected.keySet().iterator().next();
    }

    private Method findOptionalSingleMethod(Class<?> targetClass, Class<? extends Annotation> annotationType, String label) {
        Map<Method, ?> selected = MethodIntrospector.selectMethods(
            targetClass,
            (MethodIntrospector.MetadataLookup<Annotation>) method ->
                AnnotatedElementUtils.findMergedAnnotation(method, annotationType)
        );
        if (selected.isEmpty()) {
            return null;
        }
        if (selected.size() > 1) {
            throw new IllegalStateException("AIAction class must declare at most one " + label + " method (found " + selected.size() + "): " + targetClass.getName());
        }
        return selected.keySet().iterator().next();
    }

    private AIActionMetaData buildMetadata(AIAction action, Method executeMethod) {
        Map<String, String> parameters = new LinkedHashMap<>();
        Set<String> requiredParameters = new java.util.LinkedHashSet<>();

        for (Parameter parameter : executeMethod.getParameters()) {
            if (ActionMethodArgumentBinder.isContextParameter(parameter.getType())) {
                continue;
            }
            Param param = parameter.getAnnotation(Param.class);
            if (param == null) {
                throw new IllegalStateException("AIAction execute method parameter must be annotated with @Param: "
                    + executeMethod.getDeclaringClass().getName() + "#" + executeMethod.getName());
            }

            String name = StringUtils.hasText(param.value()) ? param.value() : parameter.getName();
            if (!StringUtils.hasText(name) || name.startsWith("arg")) {
                throw new IllegalStateException("AIAction parameter name is not available. "
                    + "Compile with -parameters or set @Param(\"name\"). "
                    + "Offending parameter in " + executeMethod.getDeclaringClass().getName() + "#" + executeMethod.getName());
            }

            String description = StringUtils.hasText(param.description())
                ? param.description().trim()
                : parameter.getType().getSimpleName();

            description = description + (param.required() ? " (required)" : " (optional)");
            parameters.put(name, description);
            if (param.required()) {
                requiredParameters.add(name);
            }
        }

        return AIActionMetaData.builder()
            .name(action.name())
            .description(action.description())
            .category(action.category())
            .accessMode(action.accessMode())
            .parameters(Collections.unmodifiableMap(parameters))
            .requiredParameters(Collections.unmodifiableSet(requiredParameters))
            .build();
    }

    private String normalize(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.endsWith("_action")) {
            normalized = normalized.substring(0, normalized.length() - "_action".length());
        }
        return normalized;
    }
}
