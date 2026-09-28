package com.alertas.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alertas.shared.interceptor.TenantContext;
import com.alertas.soporte.IntegracionTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

// pool de 1 conexion para asegurar que se prueba siempre la misma conexion
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class TenantSupportTest extends IntegracionTest {

    static Long colegioA;
    static Long colegioB;

    @Autowired
    EntityManager em;

    @Autowired
    TransactionTemplate tx;

    @BeforeAll
    static void datos() {
        colegioA = crearInstitucion("soporte-a");
        colegioB = crearInstitucion("soporte-b");
        OWNER.update("INSERT INTO categorias_alerta (cat_ins_id, cat_nombre) VALUES (?, 'Solo de A')", colegioA);
        OWNER.update("INSERT INTO categorias_alerta (cat_ins_id, cat_nombre) VALUES (?, 'Solo de B')", colegioB);
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
    }

    @SuppressWarnings("unchecked")
    private List<String> categorias() {
        return em.createNativeQuery("SELECT cat_nombre FROM categorias_alerta ORDER BY cat_nombre").getResultList();
    }

    @Test
    void cadaColegioVeSoloLoSuyo() {
        TenantContext.establecer(colegioA, "soporte-a");
        List<String> deA = tx.execute(s -> {
            TenantSupport.requireTenant(em);
            return categorias();
        });
        assertThat(deA).containsExactly("Solo de A");

        TenantContext.establecer(colegioB, "soporte-b");
        List<String> deB = tx.execute(s -> {
            TenantSupport.requireTenant(em);
            return categorias();
        });
        assertThat(deB).containsExactly("Solo de B");
    }

    @Test
    void sinTenantFallaEnVezDeDevolverVacio() {
        assertThatThrownBy(() -> tx.execute(s -> TenantSupport.requireTenant(em)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No hay institucion");
    }

    @Test
    void fueraDeTransaccionFalla() {
        TenantContext.establecer(colegioA, "soporte-a");
        assertThatThrownBy(() -> TenantSupport.requireTenant(em))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaccion");
    }

    @Test
    void elTenantNoQuedaPegadoEnLaConexion() {
        TenantContext.establecer(colegioA, "soporte-a");
        tx.executeWithoutResult(s -> {
            TenantSupport.requireTenant(em);
            assertThat(categorias()).hasSize(1);
        });

        // misma conexion (pool de 1), otra transaccion sin requireTenant: no debe ver nada
        List<String> despues = tx.execute(s -> categorias());
        assertThat(despues).isEmpty();
    }
}
