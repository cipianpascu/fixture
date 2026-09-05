package com.db.olorin.rest.client;

import com.db.olorin.rest.exception.ProxyConfigurationException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.HttpMethod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Injects generated-client interfaces with explicit parameter-to-HTTP mapping. */
public final class OlorinRestClientInjector implements BeanPostProcessor {
    private final TypedRestClient client;
    public OlorinRestClientInjector(TypedRestClient client) { this.client = client; }

    @Override public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        for (Field field : bean.getClass().getDeclaredFields()) {
            OlorinRestClient qualifier = field.getAnnotation(OlorinRestClient.class);
            if (qualifier == null) continue;
            if (!field.getType().isInterface()) throw new ProxyConfigurationException("OlorinRestClient field '%s' must be an interface".formatted(field.getName()));
            boolean accessible = field.canAccess(bean);
            try { field.setAccessible(true); field.set(bean, proxy(field.getType(), qualifier.value())); }
            catch (IllegalAccessException e) { throw new ProxyConfigurationException("Cannot inject REST client field '%s'".formatted(field.getName()), e); }
            finally { field.setAccessible(accessible); }
        }
        return bean;
    }

    private Object proxy(Class<?> type, String backend) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (target, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(target, method, args);
            RestOperation operation = method.getAnnotation(RestOperation.class);
            if (operation == null) throw new ProxyConfigurationException("Generated client method '%s' requires @RestOperation".formatted(method.getName()));
            Mapping mapping = map(method, args == null ? new Object[0] : args, operation.path());
            Class<?> response = method.getReturnType() == Void.TYPE ? Void.class : method.getReturnType();
            Object result = client.exchange(backend, new RestRequest<>(HttpMethod.valueOf(operation.method()), mapping.path, mapping.body, mapping.headers, mapping.query, response, mapping.cookies, Map.of(), Map.of()));
            return method.getReturnType() == Void.TYPE ? null : result;
        });
    }

    private Object objectMethod(Object target, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "OlorinRestClientProxy(" + target.getClass().getInterfaces()[0].getName() + ")";
            case "hashCode" -> System.identityHashCode(target);
            case "equals" -> target == (args == null ? null : args[0]);
            default -> throw new ProxyConfigurationException("Unsupported Object method " + method.getName());
        };
    }

    private Mapping map(Method method, Object[] args, String operationPath) {
        if (method.getParameterCount() != args.length) throw new ProxyConfigurationException("Argument mismatch for " + method.getName());
        String path = operationPath;
        Object body = null;
        Map<String, String> headers = new LinkedHashMap<>(), query = new LinkedHashMap<>(), cookies = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object value = args[i]; var parameter = method.getParameters()[i];
            if (parameter.isAnnotationPresent(RestPath.class)) path = path.replace("{" + parameter.getAnnotation(RestPath.class).value() + "}", encode(value));
            else if (parameter.isAnnotationPresent(RestQuery.class)) put(query, parameter.getAnnotation(RestQuery.class).value(), value);
            else if (parameter.isAnnotationPresent(RestHeader.class)) put(headers, parameter.getAnnotation(RestHeader.class).value(), value);
            else if (parameter.isAnnotationPresent(RestCookie.class)) put(cookies, parameter.getAnnotation(RestCookie.class).value(), value);
            else if (parameter.isAnnotationPresent(RestBody.class)) { if (body != null) throw new ProxyConfigurationException("Only one @RestBody is allowed on " + method.getName()); body = value; }
            else if (method.getParameterCount() == 1) body = value;
            else throw new ProxyConfigurationException("Every argument of " + method.getName() + " must have a REST binding annotation");
        }
        if (path.matches(".*\\{[^}]+}.*")) throw new ProxyConfigurationException("Unresolved path parameter in " + method.getName());
        return new Mapping(path, body, headers, query, cookies);
    }
    private void put(Map<String, String> target, String name, Object value) { if (value != null) target.put(name, String.valueOf(value)); }
    private String encode(Object value) { if (value == null) throw new ProxyConfigurationException("Path parameters must not be null"); return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8); }
    private record Mapping(String path, Object body, Map<String, String> headers, Map<String, String> query, Map<String, String> cookies) { }
}
