/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.monitoring.configuration;

import org.apache.commons.monitoring.MonitoringException;
import org.apache.commons.monitoring.util.ClassLoaders;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Configuration {
    private static final Logger LOGGER = Logger.getLogger(Configuration.class.getName());

    private static final Collection<ToDestroy> INSTANCES = new ArrayList<ToDestroy>();

    public static final String COMMONS_MONITORING_PREFIX = "org.apache.commons.monitoring.";
    private static final String DEFAULT_CONFIGURATION_FILE = "commons-monitoring.properties";

    private static Thread shutdownHook = null;

    private static final Properties PROPERTIES = new Properties(System.getProperties());
    static {
        try {
            final InputStream is = findConfiguration();
            if (is != null) {
                PROPERTIES.load(is);
            }
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
    }
    private static InputStream findConfiguration() throws FileNotFoundException {
        final String filename = System.getProperty(COMMONS_MONITORING_PREFIX + "configuration", DEFAULT_CONFIGURATION_FILE);
        if (new File(filename).exists()) {
            return new FileInputStream(filename);
        }

        return ClassLoaders.current().getResourceAsStream(filename);
    }

    public static <T> T[] newInstances(final Class<T> api) {
        final String names = PROPERTIES.getProperty(api.getName());
        if (names == null) {
            return (T[]) Array.newInstance(api, 0);
        }

        final String[] split = names.split(",");
        final T[] array = (T[]) Array.newInstance(api, split.length);
        for (int i = 0; i < array.length; i++) {
            try {
                array[i] = newInstance(api, split[i]);
            } catch (final Exception e) {
                throw new MonitoringException(e);
            }
        }
        return array;
    }

    public static synchronized <T> T newInstance(final Class<T> clazz) {
        try {
            String config = PROPERTIES.getProperty(clazz.getName());
            if (config == null) {
                if (clazz.isInterface()) {
                    config = clazz.getPackage().getName() + ".Default" + clazz.getSimpleName();
                } else {
                    config = clazz.getName();
                }
            }

            return newInstance(clazz, config);
        } catch (final Exception e) {
            throw new MonitoringException(e);
        }
    }

    private static <T> T newInstance(final Class<T> clazz, final String config) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?> loadedClass;
        try {
            loadedClass = ClassLoaders.current().loadClass(config);
        } catch (final Throwable throwable) { // NoClassDefFoundError or Exception
            loadedClass = clazz;
        }

        final Object instance = loadedClass.newInstance();
        for (final Method m : loadedClass.getMethods()) {
            if (m.getAnnotation(Created.class) != null) {
                m.invoke(instance);
            } else if (m.getAnnotation(Destroying.class) != null) {
                if (shutdownHook == null == is(COMMONS_MONITORING_PREFIX + "shutdown.hook", true)) {
                    shutdownHook = new Thread() {
                        @Override
                        public void run() {
                            shutdown();
                        }
                    };
                    Runtime.getRuntime().addShutdownHook(shutdownHook);
                }
                INSTANCES.add(new ToDestroy(m, instance));
            }
        }

        if (loadedClass.getAnnotation(AutoSet.class) != null) {
            Class<?> current = loadedClass;
            while (current != null && !current.isInterface() && !Object.class.equals(current)) {
                for (final Field field : loadedClass.getDeclaredFields()) {
                    final String value = PROPERTIES.getProperty(loadedClass.getName() + "." + field.getName());
                    if (value != null) {
                        final boolean acc = field.isAccessible();
                        if (!acc) {
                            field.setAccessible(true);
                        }
                        try {
                            field.set(instance, convertTo(field.getType(), value));
                        } finally {
                            if (!acc) {
                                field.setAccessible(false);
                            }
                        }
                    }
                }
                current = current.getSuperclass();
            }
        }

        return clazz.cast(instance);
    }

    public static boolean is(final String key, final boolean defaultValue) {
        return Boolean.parseBoolean(getProperty(key, Boolean.toString(defaultValue)));
    }

    public static int getInteger(final String key, final int defaultValue) {
        return Integer.parseInt(getProperty(key, Integer.toString(defaultValue)));
    }

    public static String getProperty(final String key, final String defaultValue) {
        return PROPERTIES.getProperty(key, defaultValue);
    }

    public static void shutdown() {
        for (final ToDestroy c : INSTANCES) {
            c.destroy();
        }
        INSTANCES.clear();
    }

    private Configuration() {
        // no-op
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Created {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Destroying {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public static @interface AutoSet {
    }

    private static Object convertTo(final Class<?> type, final String value) {
        if (String.class.equals(type)) {
            return value;
        }
        if (int.class.equals(type)) {
            return Integer.parseInt(value);
        }
        if (long.class.equals(type)) {
            return Long.parseLong(value);
        }
        throw new IllegalArgumentException("Type " + type.getName() + " not supported");
    }

    private static class ToDestroy {
        private final Method method;
        private final Object target;

        public ToDestroy(final Method m, final Object instance) {
            this.method = m;
            this.target = instance;
        }

        public void destroy() {
            try {
                method.invoke(target);
            } catch (final Exception e) {
                // no-op
            }
        }
    }
}
