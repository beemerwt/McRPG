package com.github.beemerwt.mcrpg.util;

import blue.endless.jankson.*;
import com.github.beemerwt.mcrpg.annotation.*;

import java.lang.reflect.*;
import java.util.*;

/**
 * Reflection-based JSON5 mapper for classes annotated with @JanksonObject.
 * - Includes all non-static, non-transient fields by default.
 * - Exclude with @JankIgnore.
 * - Optional: @JankKey to rename; @JankComment to write comments.
 * Supports primitives, wrappers, String, enums, arrays, List<T>, Map<String,T>, and nested @JanksonObject.
 */
public final class JanksonSerde {
    private JanksonSerde() {}

    /* ========== Public API ========== */
    public static JsonObject toJson(Object pojo) {
        JsonObject out = new JsonObject();

        // Walk class hierarchy from base -> derived
        List<Class<?>> chain = hierarchy(pojo.getClass());
        for (Class<?> c : chain) {
            List<Field> fields = new ArrayList<>(Arrays.asList(c.getDeclaredFields()));
            // Filter out static/transient/ignored
            fields.removeIf(f -> Modifier.isStatic(f.getModifiers())
                    || Modifier.isTransient(f.getModifiers())
                    || f.isAnnotationPresent(JankIgnore.class));

            for (Field f : fields) {
                f.setAccessible(true);
                String key = keyFor(f);
                Object val = getFieldValue(pojo, f);
                JsonElement el = toElement(val);
                out.put(key, el);
                if (f.isAnnotationPresent(JankComment.class))
                    out.setComment(key, f.getAnnotation(JankComment.class).value());
            }
        }
        return out;
    }

    /** Populate an existing instance from a JsonObject, keeping field defaults for missing keys. */
    public static <T> T fillFrom(JsonObject obj, T instance) {
        if (obj == null || instance == null) return instance;
        Class<?> type = instance.getClass();
        if (!type.isAnnotationPresent(JanksonObject.class)) return instance;

        for (Field f : fieldsOf(type)) {
            if (!shouldInclude(f)) continue;
            String key = keyFor(f);
            JsonElement raw = obj.get(key);
            if (raw == null || raw instanceof JsonNull) continue; // keep default
            setFieldValueFromJson(instance, f, raw);
        }
        return instance;
    }

    /* ========== Core conversion ========== */

    private static JsonElement toElement(Object v) {
        if (v == null) return JsonNull.INSTANCE;

        // Try primitive conversion via your helper (expects it in your project).
        JsonElement prim = JanksonConvert.toJson(v);
        if (!(prim instanceof JsonNull)) return prim;

        switch (v) {
            case Enum<?> e -> {
                return JsonPrimitive.of(e.name());
            }
            case Map<?, ?> m -> {
                JsonObject o = new JsonObject();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getKey() == null) continue;
                    o.put(String.valueOf(e.getKey()), toElement(e.getValue()));
                }
                return o;
            }
            case Iterable<?> it -> {
                JsonArray a = new JsonArray();
                for (Object x : it) a.add(toElement(x));
                return a;
            }
            default -> {
            }
        }

        if (v.getClass().isArray()) {
            JsonArray a = new JsonArray();
            int n = Array.getLength(v);
            for (int i = 0; i < n; i++) a.add(toElement(Array.get(v, i)));
            return a;
        }

        if (v.getClass().isAnnotationPresent(JanksonObject.class)) {
            return toJson(v);
        }

        return JsonPrimitive.of(String.valueOf(v));
    }

    private static List<Class<?>> hierarchy(Class<?> leaf) {
        Deque<Class<?>> stack = new ArrayDeque<>();
        for (Class<?> c = leaf; c != null && c != Object.class; c = c.getSuperclass()) {
            stack.push(c);
        }
        return new ArrayList<>(stack); // base first
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void setFieldValueFromJson(Object target, Field f, JsonElement raw) {
        Class<?> ft = f.getType();
        try {
            if (ft == String.class) { f.set(target, asString(raw, (String) f.get(target))); return; }
            if (ft == boolean.class || ft == Boolean.class) { f.set(target, asBoolean(raw, (Boolean) f.get(target))); return; }
            if (ft == int.class || ft == Integer.class) { f.set(target, asInt(raw, (Integer) f.get(target))); return; }
            if (ft == long.class || ft == Long.class) { f.set(target, asLong(raw, (Long) f.get(target))); return; }
            if (ft == double.class || ft == Double.class) { f.set(target, asDouble(raw, (Double) f.get(target))); return; }
            if (ft == float.class || ft == Float.class) {
                double d = asDouble(raw, ((Number) f.get(target)).doubleValue());
                f.set(target, (float) d);
                return;
            }
            if (Enum.class.isAssignableFrom(ft)) {
                String s = asString(raw, null);
                if (s != null) f.set(target, Enum.valueOf((Class<? extends Enum>) ft, s.trim().toUpperCase()));
                return;
            }

            if (List.class.isAssignableFrom(ft)) {
                Type g = f.getGenericType();
                Class<?> elemType = Object.class;
                if (g instanceof ParameterizedType pt) {
                    Type t = pt.getActualTypeArguments()[0];
                    if (t instanceof Class<?> c) elemType = c;
                }
                List list = new ArrayList();
                if (raw instanceof JsonArray arr) for (JsonElement el : arr) list.add(coerce(el, elemType, null));
                f.set(target, list); return;
            }

            if (Map.class.isAssignableFrom(ft)) {
                Type g = f.getGenericType();
                Class<?> valType = Object.class;
                if (g instanceof ParameterizedType pt) {
                    Type vt = pt.getActualTypeArguments()[1];
                    if (vt instanceof Class<?> c) valType = c;
                }
                Map<String, Object> map = new LinkedHashMap<>();
                if (raw instanceof JsonObject o) {
                    for (String k : o.keySet().stream().toList()) {
                        map.put(k, coerce(o.get(k), valType, null));
                    }
                }
                f.set(target, map);
                return;
            }

            if (ft.isAnnotationPresent(JanksonObject.class)) {
                Object child = f.get(target);
                if (child == null) child = newInstance(ft);
                if (raw instanceof JsonObject o) fillFrom(o, child);
                f.set(target, child); return;
            }

            // Else: leave default
        } catch (Throwable ignored) {}
    }

    private static Object coerce(JsonElement raw, Class<?> want, Object def) {
        if (want == String.class) return asString(raw, (String) def);
        if (want == Boolean.class || want == boolean.class) return asBoolean(raw, (Boolean) def);
        if (want == Integer.class || want == int.class) return asInt(raw, (Integer) def);
        if (want == Long.class || want == long.class) return asLong(raw, (Long) def);
        if (want == Double.class || want == double.class) return asDouble(raw, (Double) def);
        if (want == Float.class || want == float.class) {
            double d = asDouble(raw, def instanceof Number ? ((Number) def).doubleValue() : 0.0);
            return (float) d;
        }

        if (Enum.class.isAssignableFrom(want) && raw instanceof JsonPrimitive p) {
            try {
                @SuppressWarnings("unchecked") Class<? extends Enum> ec = (Class<? extends Enum>) want;
                return Enum.valueOf(ec, p.asString().trim().toUpperCase());
            } catch (Throwable ignored) {}
        }

        if (want.isAnnotationPresent(JanksonObject.class) && raw instanceof JsonObject o) {
            Object inst = newInstance(want); fillFrom(o, inst); return inst;
        }

        if (raw instanceof JsonPrimitive p) return p.asString();
        return def;
    }

    /* ========== Helpers ========== */

    private static boolean shouldInclude(Field f) {
        int mod = f.getModifiers();
        if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) return false;
        return f.getAnnotation(JankIgnore.class) == null;
    }

    private static String keyFor(Field f) {
        JankKey k = f.getAnnotation(JankKey.class);
        if (k != null && !k.value().isEmpty()) {
            return k.value();
        }

        JankProperty p = f.getAnnotation(JankProperty.class);
        if (p != null && !p.name().isEmpty()) {
            return p.name();
        }

        return f.getName();
    }

    private static List<Field> fieldsOf(Class<?> c) {
        ArrayList<Field> out = new ArrayList<>();
        for (Class<?> t = c; t != null && t != Object.class; t = t.getSuperclass()) {
            for (Field f : t.getDeclaredFields()) { f.setAccessible(true); out.add(f); }
        }
        return out;
    }

    private static Object getFieldValue(Object o, Field f) {
        try { return f.get(o); } catch (Throwable ignored) { return null; }
    }

    private static Object newInstance(Class<?> c) {
        try {
            var ct = c.getDeclaredConstructor();
            ct.setAccessible(true);
            return ct.newInstance();
        } catch (Throwable t) {
            try { return c.getDeclaredConstructor().newInstance(); }
            catch (Throwable ignored) { return null; }
        }
    }

    private static String asString(JsonElement e, String def) {
        if (e instanceof JsonPrimitive p) {
            String s = p.asString();
            return !s.isEmpty() ? s : def;
        }
        return def;
    }

    private static boolean asBoolean(JsonElement e, Boolean def) {
        if (e instanceof JsonPrimitive p) {
            String s = p.asString();
            s = s.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
            if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
        }
        return def != null && def;
    }

    private static int asInt(JsonElement e, Integer def) {
        if (e instanceof JsonPrimitive p) {
            try { return (int) Long.parseLong(p.asString().trim()); } catch (Throwable ignored) {}
            try { return (int) Double.parseDouble(p.asString().trim()); } catch (Throwable ignored) {}
        }
        return def != null ? def : 0;
    }

    private static long asLong(JsonElement e, Long def) {
        if (e instanceof JsonPrimitive p) {
            try { return Long.parseLong(p.asString().trim()); } catch (Throwable ignored) {}
            try { return (long) Double.parseDouble(p.asString().trim()); } catch (Throwable ignored) {}
        }
        return def != null ? def : 0L;
    }

    private static double asDouble(JsonElement e, Double def) {
        if (e instanceof JsonPrimitive p) {
            try { return Double.parseDouble(p.asString().trim()); } catch (Throwable ignored) {}
        }
        return def != null ? def : 0.0;
    }
}

