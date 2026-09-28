package com.alertas.shared;

import com.alertas.shared.interceptor.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class TenantSupport {

    private TenantSupport() {
    }

    // va al inicio de cada metodo @Transactional que toque datos de una institucion.
    // el set_config con true solo dura la transaccion, asi no queda pegado en la conexion del pool
    public static Long requireTenant(EntityManager em) {

        Long institucionId = TenantContext.getInstitucionId();

        if (institucionId == null) {
            throw new IllegalStateException("No hay institucion en el contexto de la solicitud");
        }

        fijarEnBaseDeDatos(em, institucionId);
        return institucionId;
    }

    // el superadmin no tiene institucion en el token, elige con cual trabajar.
    // se deja en el contexto para que los demas servicios usen requireTenant normal.
    // el TenantInterceptor lo limpia al terminar la solicitud
    public static void usarInstitucion(EntityManager em, Long institucionId) {

        TenantContext.establecer(institucionId, null);
        fijarEnBaseDeDatos(em, institucionId);
    }

    private static void fijarEnBaseDeDatos(EntityManager em, Long institucionId) {

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("requireTenant se debe llamar dentro de una transaccion");
        }

        em.createNativeQuery("SELECT set_config('app.current_tenant_id', ?1, true)")
                .setParameter(1, institucionId.toString())
                .getSingleResult();
    }
}
