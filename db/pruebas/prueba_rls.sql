-- Prueba de aislamiento entre instituciones y de las reglas del esquema.
-- Corre dentro de una transacción que termina en ROLLBACK: no deja datos.
-- Uso: db/probar.sh
\set ON_ERROR_STOP on
BEGIN;

-- ---------------------------------------------------------------------------
-- Datos de prueba, creados como dueño (el dueño no está sujeto a RLS)
-- ---------------------------------------------------------------------------
SET LOCAL ROLE alertas_owner;

INSERT INTO instituciones (ins_id, ins_nombre, ins_slug) OVERRIDING SYSTEM VALUE VALUES
    (900001, 'Colegio A', 'colegio-a'),
    (900002, 'Colegio B', 'colegio-b');

INSERT INTO usuarios (usu_id, usu_ins_id, usu_usuario, usu_contrasena_hash, usu_rol) OVERRIDING SYSTEM VALUE VALUES
    (910001, 900001, '1001', 'x', 'ESTUDIANTE'),
    (910002, 900002, '1001', 'x', 'ESTUDIANTE'),     -- mismo documento en otra institución: permitido
    (910003, 900001, '2001', 'x', 'PSICORIENTADOR'),
    (910004, 900001, '3001', 'x', 'DOCENTE');

INSERT INTO estudiantes (est_id, est_ins_id, est_usu_id, est_tipo_doc, est_nro_doc, est_nombres, est_apellidos)
OVERRIDING SYSTEM VALUE VALUES
    (920001, 900001, 910001, 'TI', '1001', 'José', 'Pérez'),
    (920002, 900002, 910002, 'TI', '1001', 'José', 'Pérez');

INSERT INTO personal (per_id, per_ins_id, per_usu_id, per_tipo_doc, per_nro_doc, per_nombres, per_apellidos)
OVERRIDING SYSTEM VALUE VALUES (930001, 900001, 910003, 'CC', '2001', 'Ana', 'Gómez');

INSERT INTO anios_lectivos (anl_id, anl_ins_id, anl_anio, anl_activo) OVERRIDING SYSTEM VALUE VALUES
    (940001, 900001, 2026, true),
    (940002, 900002, 2026, true);
INSERT INTO grados (gra_id, gra_ins_id, gra_nombre, gra_orden) OVERRIDING SYSTEM VALUE VALUES
    (950001, 900001, 'Sexto', 6),
    (950002, 900002, 'Sexto', 6);
INSERT INTO grupos (grp_id, grp_ins_id, grp_anl_id, grp_gra_id, grp_nombre) OVERRIDING SYSTEM VALUE VALUES
    (960001, 900001, 940001, 950001, 'A'),
    (960002, 900002, 940002, 950002, 'A');
INSERT INTO categorias_alerta (cat_id, cat_ins_id, cat_nombre) OVERRIDING SYSTEM VALUE VALUES
    (970001, 900001, 'Convivencia');

-- ---------------------------------------------------------------------------
-- 1. Restricciones de integridad (como dueño)
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    -- Ubicar a un estudiante de A en un grupo de B
    BEGIN
        INSERT INTO ubicaciones (ubi_ins_id, ubi_est_id, ubi_anl_id, ubi_grp_id) VALUES (900001, 920001, 940001, 960002);
        RAISE EXCEPTION 'FALLO: se permitió ubicar en un grupo de otra institución';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'OK  no se ubica en un grupo de otra institución';
    END;

    -- Familiar marcado "recibe SMS" sin celular
    BEGIN
        INSERT INTO familiares (fam_ins_id, fam_est_id, fam_posicion, fam_nombres, fam_parentesco)
        VALUES (900001, 920001, 1, 'Rosa', 'ABUELO');
        RAISE EXCEPTION 'FALLO: recibe SMS sin celular';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  recibe SMS exige celular';
    END;
END $$;

INSERT INTO familiares (fam_ins_id, fam_est_id, fam_posicion, fam_nombres, fam_parentesco, fam_celular)
VALUES (900001, 920001, 1, 'María', 'MADRE', '3001234567'),
       (900001, 920001, 2, 'Luis',  'PADRE', '3001234568'),
       (900001, 920001, 3, 'Rosa',  'ABUELO', '3001234569');

DO $$
BEGIN
    BEGIN
        INSERT INTO familiares (fam_ins_id, fam_est_id, fam_posicion, fam_nombres, fam_parentesco)
        VALUES (900001, 920001, 4, 'Pedro', 'TIO');
        RAISE EXCEPTION 'FALLO: se permitió un cuarto familiar';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  cuarto familiar rechazado';
    END;

    -- Dos años activos en la misma institución
    BEGIN
        INSERT INTO anios_lectivos (anl_ins_id, anl_anio, anl_activo) VALUES (900001, 2027, true);
        RAISE EXCEPTION 'FALLO: se permitieron dos años activos';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK  un solo año activo';
    END;

    -- Documento repetido en la misma institución (aunque cambie el tipo)
    BEGIN
        INSERT INTO usuarios (usu_ins_id, usu_usuario, usu_contrasena_hash, usu_rol) VALUES (900001, '9999', 'x', 'ESTUDIANTE');
        INSERT INTO estudiantes (est_ins_id, est_usu_id, est_tipo_doc, est_nro_doc, est_nombres, est_apellidos)
        VALUES (900001, currval(pg_get_serial_sequence('usuarios', 'usu_id')), 'RC', '1001', 'Otro', 'Niño');
        RAISE EXCEPTION 'FALLO: se permitió un documento repetido';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK  documento único por institución';
    END;
END $$;

-- ---------------------------------------------------------------------------
-- 2. Aislamiento con el rol de la aplicación
-- ---------------------------------------------------------------------------
SET LOCAL ROLE alertas_app;

-- Sin tenant: no se ve nada
SELECT set_config('app.current_tenant_id', '', true);
DO $$
BEGIN
    ASSERT (SELECT count(*) FROM estudiantes) = 0, 'FALLO: sin tenant se ven estudiantes';
    ASSERT (SELECT count(*) FROM usuarios) = 0,    'FALLO: sin tenant se ven usuarios';
    RAISE NOTICE 'OK  sin tenant no se ve nada';
END $$;

-- Con el tenant A
SELECT set_config('app.current_tenant_id', '900001', true);
DO $$
DECLARE
    filas integer;
BEGIN
    ASSERT (SELECT count(*) FROM estudiantes) = 1,                          'FALLO: A debería ver 1 estudiante';
    ASSERT (SELECT count(*) FROM estudiantes WHERE est_id = 920002) = 0,    'FALLO: A ve un estudiante de B';
    ASSERT (SELECT count(*) FROM grupos) = 1,                               'FALLO: A ve grupos de B';
    RAISE NOTICE 'OK  A solo ve sus filas';

    -- Modificar y borrar filas de B: no afecta nada
    UPDATE estudiantes SET est_nombres = 'Hackeado' WHERE est_id = 920002;
    GET DIAGNOSTICS filas = ROW_COUNT;
    ASSERT filas = 0, 'FALLO: A modificó un estudiante de B';

    DELETE FROM grupos WHERE grp_id = 960002;
    GET DIAGNOSTICS filas = ROW_COUNT;
    ASSERT filas = 0, 'FALLO: A borró un grupo de B';
    RAISE NOTICE 'OK  A no modifica ni borra filas de B';

    -- Insertar una fila con el tenant de B
    BEGIN
        INSERT INTO categorias_alerta (cat_ins_id, cat_nombre) VALUES (900002, 'Intrusa');
        RAISE EXCEPTION 'FALLO: A insertó en B';
    EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'OK  A no inserta con el tenant de B';
    END;

    -- Mover una fila propia a B
    BEGIN
        UPDATE categorias_alerta SET cat_ins_id = 900002 WHERE cat_id = 970001;
        RAISE EXCEPTION 'FALLO: A movió una fila a B';
    EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'OK  A no mueve filas a B';
    END;

    -- La bitácora no se puede modificar
    BEGIN
        INSERT INTO bitacora (bit_ins_id, bit_usu_id, bit_accion, bit_entidad) VALUES (900001, 910003, 'PRUEBA', 'prueba');
        UPDATE bitacora SET bit_accion = 'ALTERADA';
        RAISE EXCEPTION 'FALLO: se modificó la bitácora';
    EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'OK  bitácora de solo inserción';
    END;
END $$;

-- ---------------------------------------------------------------------------
-- 3. Flujo de alertas y citas (tenant A, rol de la aplicación)
-- ---------------------------------------------------------------------------
INSERT INTO ubicaciones (ubi_ins_id, ubi_est_id, ubi_anl_id, ubi_grp_id) VALUES (900001, 920001, 940001, 960001);

INSERT INTO alertas (ale_ins_id, ale_est_id, ale_origen, ale_reportada_por, ale_cat_id, ale_nivel,
                     ale_descripcion, ale_anl_id, ale_grp_id)
VALUES (900001, 920001, 'DOCENTE', 910004, 970001, 'CRITICO', 'Descripción de prueba de la alerta', 940001, 960001);

DO $$
BEGIN
    ASSERT (SELECT ale_prioritaria FROM alertas LIMIT 1), 'FALLO: una alerta crítica debería ser prioritaria';

    -- EN_PROCESO sin psicorientador
    BEGIN
        UPDATE alertas SET ale_estado = 'EN_PROCESO';
        RAISE EXCEPTION 'FALLO: alerta en proceso sin psicorientador';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  en proceso exige psicorientador';
    END;

    -- Campos de solicitud del estudiante en una alerta de docente
    BEGIN
        UPDATE alertas SET ale_horario_seguro = 'Mañana';
        RAISE EXCEPTION 'FALLO: campos de estudiante en alerta de docente';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  campos de solicitud solo para estudiantes';
    END;
END $$;

UPDATE alertas SET ale_estado = 'EN_PROCESO', ale_psi_id = 930001, ale_asignada_en = now();

INSERT INTO citas (cit_ins_id, cit_est_id, cit_psi_id, cit_inicio, cit_fin, cit_modalidad, cit_creada_por)
VALUES (900001, 920001, 930001, '2026-10-01 10:00-05', '2026-10-01 10:45-05', 'PRESENCIAL', 910003);

INSERT INTO citas_alertas (cia_ins_id, cia_cit_id, cia_ale_id, cia_est_id)
SELECT 900001, c.cit_id, a.ale_id, 920001 FROM citas c, alertas a;

DO $$
BEGIN
    -- Segunda cita programada para el mismo estudiante
    BEGIN
        INSERT INTO citas (cit_ins_id, cit_est_id, cit_psi_id, cit_inicio, cit_fin, cit_modalidad, cit_creada_por)
        VALUES (900001, 920001, 930001, '2026-10-02 10:00-05', '2026-10-02 10:45-05', 'PRESENCIAL', 910003);
        RAISE EXCEPTION 'FALLO: dos citas programadas para el mismo estudiante';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK  una sola cita programada por estudiante';
    END;
END $$;

-- Cerrar la cita y probar el cruce de horario con otro estudiante
UPDATE citas SET cit_estado = 'REALIZADA', cit_cerrada_en = now();
SET LOCAL ROLE alertas_owner;
INSERT INTO usuarios (usu_id, usu_ins_id, usu_usuario, usu_contrasena_hash, usu_rol) OVERRIDING SYSTEM VALUE
VALUES (910005, 900001, '1002', 'x', 'ESTUDIANTE');
INSERT INTO estudiantes (est_id, est_ins_id, est_usu_id, est_tipo_doc, est_nro_doc, est_nombres, est_apellidos)
OVERRIDING SYSTEM VALUE VALUES (920003, 900001, 910005, 'TI', '1002', 'Laura', 'Ríos');
SET LOCAL ROLE alertas_app;
SELECT set_config('app.current_tenant_id', '900001', true);

DO $$
BEGIN
    BEGIN
        INSERT INTO citas (cit_ins_id, cit_est_id, cit_psi_id, cit_inicio, cit_fin, cit_modalidad, cit_creada_por)
        VALUES (900001, 920003, 930001, '2026-10-01 10:30-05', '2026-10-01 11:15-05', 'VIRTUAL', 910003);
        RAISE EXCEPTION 'FALLO: se permitieron citas cruzadas del mismo psicorientador';
    EXCEPTION WHEN exclusion_violation THEN RAISE NOTICE 'OK  sin citas cruzadas';
    END;

    -- Relacionar la cita de un estudiante con la alerta de otro
    BEGIN
        INSERT INTO citas (cit_ins_id, cit_est_id, cit_psi_id, cit_inicio, cit_fin, cit_modalidad, cit_creada_por)
        VALUES (900001, 920003, 930001, '2026-10-01 12:00-05', '2026-10-01 12:45-05', 'VIRTUAL', 910003);
        INSERT INTO citas_alertas (cia_ins_id, cia_cit_id, cia_ale_id, cia_est_id)
        SELECT 900001, (SELECT max(cit_id) FROM citas), ale_id, 920003 FROM alertas;
        RAISE EXCEPTION 'FALLO: cita de un estudiante con alerta de otro';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'OK  cita y alerta del mismo estudiante';
    END;

    -- Búsqueda sin tildes
    ASSERT (SELECT count(*) FROM estudiantes WHERE est_busqueda LIKE '%' || lower(f_unaccent('JOSE PEREZ')) || '%') = 1,
        'FALLO: la búsqueda sin tildes no encuentra a José Pérez';
    RAISE NOTICE 'OK  búsqueda sin tildes';
END $$;

\echo '=== TODAS LAS PRUEBAS PASARON ==='
ROLLBACK;
