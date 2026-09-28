package com.alertas.shared.interceptor;

// guarda la institucion de la solicitud que se esta atendiendo
public final class TenantContext {

    private static final ThreadLocal<Long> INSTITUCION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SLUG = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void establecer(Long institucionId, String slug) {

        INSTITUCION_ID.set(institucionId);
        SLUG.set(slug);
    }

    // null si la solicitud no tiene institucion (ej. superadmin)
    public static Long getInstitucionId() {
        return INSTITUCION_ID.get();
    }

    public static String getSlug() {
        return SLUG.get();
    }

    public static void limpiar() {

        INSTITUCION_ID.remove();
        SLUG.remove();
    }
}
