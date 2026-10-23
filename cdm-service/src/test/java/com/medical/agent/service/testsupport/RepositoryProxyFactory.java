package com.medical.agent.service.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class RepositoryProxyFactory {

    private RepositoryProxyFactory() {
    }

    public static <T> T create(Class<T> repositoryType, Map<String, Function<Object[], Object>> handlers) {
        InvocationHandler invocationHandler = (proxy, method, args) -> {
            String methodName = method.getName();
            if ("toString".equals(methodName)) {
                return repositoryType.getSimpleName() + "Proxy";
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }

            Function<Object[], Object> handler = handlers.get(methodName);
            if (handler == null && "saveAndFlush".equals(methodName)) {
                handler = handlers.get("save");
            }
            if (handler != null) {
                return handler.apply(args == null ? new Object[0] : args);
            }
            return defaultValue(method);
        };

        Object proxy = Proxy.newProxyInstance(
                repositoryType.getClassLoader(),
                new Class<?>[]{repositoryType},
                invocationHandler
        );
        return repositoryType.cast(proxy);
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            if (Optional.class.isAssignableFrom(returnType)) {
                return Optional.empty();
            }
            if (List.class.isAssignableFrom(returnType)) {
                return List.of();
            }
            if (Set.class.isAssignableFrom(returnType)) {
                return Set.of();
            }
            if (Map.class.isAssignableFrom(returnType)) {
                return Map.of();
            }
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0f;
        }
        if (double.class.equals(returnType)) {
            return 0d;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        return null;
    }
}
