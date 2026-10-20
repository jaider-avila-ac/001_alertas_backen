-- prueba de aislamiento y reglas del esquema. termina en rollback
\set ON_ERROR_STOP on
BEGIN;

-- datos de prueba como owner (no le aplica RLS)
SET LOCAL ROLE alertas_owner;

INSERT INTO instituciones (ins_id, ins_nombre, ins_slug) OVERRIDING SYSTEM VALUE VALUES
    (900001, 'Colegio A', 'colegio-a'),
    (900002, 'Colegio B', 'colegio-b');

INSERT INTO usuarios (usu_id, usu_ins_id, usu_usuario, usu_contrasena_hash, usu_rol) OVERRIDING SYSTEM VALUE VALUES
    (910001, 900001, '1001', 'x', 'ESTUDIANTE'),
    (910002, 900002, '1001', 'x', 'ESTUDIANTE'),     -- mismo documento en otra institucion, se permite
    (910003, 900001, '2001', 'x', 'PSICORIENTADOR'),
    (910004, 900001, '3001', 'x', 'DOCENTE');

INSERT INTO estudiantes (est_id, est_ins_id, est_usu_id, est_codigo, est_codigo_qr, est_tipo_doc, est_nro_doc, est_nombres, est_apellidos)
OVERRIDING SYSTEM VALUE VALUES
    (920001, 900001, 910001, 'estA', 'qrA', 'TI', '1001', 'José', 'Pérez'),
    (920002, 900002, 910002, 'estB', 'qrB', 'TI', '1001', 'José', 'Pérez');

INSERT INTO personal (per_id, per_ins_id, per_usu_id, per_codigo, per_tipo_doc, per_nro_doc, per_nombres, per_apellidos)
OVERRIDING SYSTEM VALUE VALUES (930001, 900001, 910003, 'perA', 'CC', '2001', 'Ana', 'Gomez');

INSERT INTO familiares (fam_ins_id, fam_est_id, fam_posicion, fam_nombres, fam_parentesco, fam_celular)
VALUES (900002, 920002, 1, 'Marta', 'MADRE', '3001234560');

-- 1. restricciones
DO $$
BEGIN
    -- una alerta de A sobre un estudiante de B
    BEGIN
        INSERT INTO alertas (ale_ins_id, ale_codigo, ale_est_id, ale_origen, ale_reportada_por, ale_cat_id, ale_nivel, ale_descripcion)
        VALUES (900001, 'aleX', 920002, 'DOCENTE', 910004, (SELECT min(cat_id) FROM categorias_alerta), 'LEVE', 'Descripcion de prueba de la alerta');
        RAISE EXCEPTION 'FALLO: se permitio usar un estudiante de otra institucion';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'OK  no se mezclan datos de dos instituciones';
    END;

    -- familiar marcado "recibe SMS" sin celular
    BEGIN
        INSERT INTO familiares (fam_ins_id, fam_est_id, fam_posicion, fam_nombres, fam_parentesco)
        VALUES (900001, 920001, 1, 'Rosa', 'ABUELO');
        RAISE EXCEPTION 'FALLO: recibe SMS sin celular';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  recibe SMS exige celular';
    END;
END $$;

INSERT INTO familiares (fam_ins_id, fam_est_id, fam_posicion, fam_nombres, fam_parentesco, fam_celular)
VALUES (900001, 920001, 1, 'Maria', 'MADRE', '3001234567'),
       (900001, 920001, 2, 'Luis',  'PADRE', '3001234568'),
       (900001, 920001, 3, 'Rosa',  'ABUELO', '3001234569');

DO $$
BEGIN
    BEGIN
        INSERT INTO familiares (fam_ins_id, fam_est_id, fam_posicion, fam_nombres, fam_parentesco)
        VALUES (900001, 920001, 4, 'Pedro', 'TIO');
        RAISE EXCEPTION 'FALLO: se permitio un cuarto familiar';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  cuarto familiar rechazado';
    END;


    -- documento repetido en la misma institucion (aunque cambie el tipo)
    BEGIN
        INSERT INTO usuarios (usu_ins_id, usu_usuario, usu_contrasena_hash, usu_rol) VALUES (900001, '9999', 'x', 'ESTUDIANTE');
        INSERT INTO estudiantes (est_ins_id, est_usu_id, est_codigo, est_codigo_qr, est_tipo_doc, est_nro_doc, est_nombres, est_apellidos)
        VALUES (900001, currval(pg_get_serial_sequence('usuarios', 'usu_id')), 'estX', 'qrX', 'RC', '1001', 'Otro', 'Nino');
        RAISE EXCEPTION 'FALLO: se permitio un documento repetido';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK  documento unico por institucion';
    END;
END $$;

-- 2. aislamiento con alertas_app
SET LOCAL ROLE alertas_app;

-- sin tenant: no se ve nada
SELECT set_config('app.current_tenant_id', '', true);
DO $$
BEGIN
    ASSERT (SELECT count(*) FROM estudiantes) = 0, 'FALLO: sin tenant se ven estudiantes';
    ASSERT (SELECT count(*) FROM usuarios) = 0,    'FALLO: sin tenant se ven usuarios';
    RAISE NOTICE 'OK  sin tenant no se ve nada';
END $$;

-- con el tenant A
SELECT set_config('app.current_tenant_id', '900001', true);
DO $$
DECLARE
    filas integer;
BEGIN
    ASSERT (SELECT count(*) FROM estudiantes) = 1,                          'FALLO: A deberia ver 1 estudiante';
    ASSERT (SELECT count(*) FROM estudiantes WHERE est_id = 920002) = 0,    'FALLO: A ve un estudiante de B';
    ASSERT (SELECT count(*) FROM familiares WHERE fam_ins_id = 900002) = 0, 'FALLO: A ve familiares de B';
    -- el catalogo de categorias es comun: lo ven todas
    ASSERT (SELECT count(*) FROM categorias_alerta) = 10,                   'FALLO: el catalogo deberia tener 10 categorias';
    RAISE NOTICE 'OK  A solo ve sus filas';

    -- modificar y borrar filas de B: no afecta nada
    UPDATE estudiantes SET est_nombres = 'Hackeado' WHERE est_id = 920002;
    GET DIAGNOSTICS filas = ROW_COUNT;
    ASSERT filas = 0, 'FALLO: A modifico un estudiante de B';

    DELETE FROM familiares WHERE fam_est_id = 920002;
    GET DIAGNOSTICS filas = ROW_COUNT;
    ASSERT filas = 0, 'FALLO: A borro un familiar de B';
    RAISE NOTICE 'OK  A no modifica ni borra filas de B';

    -- insertar una fila con el tenant de B
    BEGIN
        INSERT INTO familiares (fam_ins_id, fam_est_id, fam_posicion, fam_nombres, fam_parentesco)
        VALUES (900002, 920002, 2, 'Intruso', 'OTRO');
        RAISE EXCEPTION 'FALLO: A inserto en B';
    EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'OK  A no inserta con el tenant de B';
    END;

    -- mover una fila propia a B
    BEGIN
        UPDATE estudiantes SET est_ins_id = 900002 WHERE est_id = 920001;
        RAISE EXCEPTION 'FALLO: A movio una fila a B';
    EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'OK  A no mueve filas a B';
    END;

    -- el catalogo de categorias no se toca desde la aplicacion
    BEGIN
        INSERT INTO categorias_alerta (cat_nombre) VALUES ('Intrusa');
        RAISE EXCEPTION 'FALLO: la aplicacion agrego una categoria';
    EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'OK  catalogo de categorias de solo lectura';
    END;

    -- la bitacora no se puede modificar
    BEGIN
        INSERT INTO bitacora (bit_ins_id, bit_usu_id, bit_accion, bit_entidad) VALUES (900001, 910003, 'PRUEBA', 'prueba');
        UPDATE bitacora SET bit_accion = 'ALTERADA';
        RAISE EXCEPTION 'FALLO: se modifico la bitacora';
    EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'OK  bitacora de solo insercion';
    END;
END $$;

-- 3. alertas y citas
INSERT INTO alertas (ale_ins_id, ale_codigo, ale_est_id, ale_origen, ale_reportada_por, ale_cat_id, ale_nivel,
                     ale_descripcion)
VALUES (900001, 'aleA', 920001, 'DOCENTE', 910004, (SELECT min(cat_id) FROM categorias_alerta), 'CRITICO', 'Descripcion de prueba de la alerta');

DO $$
BEGIN
    ASSERT (SELECT ale_prioritaria FROM alertas LIMIT 1), 'FALLO: una alerta critica deberia ser prioritaria';

    -- completada exige fecha de cierre
    BEGIN
        UPDATE alertas SET ale_estado = 'COMPLETADA';
        RAISE EXCEPTION 'FALLO: alerta completada sin fecha';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  completada exige fecha';
    END;

    -- campos de solicitud del estudiante en una alerta de docente
    BEGIN
        UPDATE alertas SET ale_horario_seguro = 'Manana';
        RAISE EXCEPTION 'FALLO: campos de estudiante en alerta de docente';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  campos de solicitud solo para estudiantes';
    END;
END $$;

UPDATE alertas SET ale_estado = 'EN_PROCESO', ale_psi_id = 930001, ale_asignada_en = now();

INSERT INTO citas (cit_ins_id, cit_codigo, cit_est_id, cit_psi_id, cit_inicio, cit_fin, cit_modalidad, cit_creada_por)
VALUES (900001, 'citA', 920001, 930001, '2026-10-01 10:00-05', '2026-10-01 10:45-05', 'PRESENCIAL', 910003);

INSERT INTO citas_alertas (cia_ins_id, cia_cit_id, cia_ale_id, cia_est_id)
SELECT 900001, c.cit_id, a.ale_id, 920001 FROM citas c, alertas a;

DO $$
BEGIN
    -- segunda cita programada para el mismo estudiante
    BEGIN
        INSERT INTO citas (cit_ins_id, cit_codigo, cit_est_id, cit_psi_id, cit_inicio, cit_fin, cit_modalidad, cit_creada_por)
        VALUES (900001, 'citB', 920001, 930001, '2026-10-02 10:00-05', '2026-10-02 10:45-05', 'PRESENCIAL', 910003);
        RAISE EXCEPTION 'FALLO: dos citas programadas para el mismo estudiante';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK  una sola cita programada por estudiante';
    END;
END $$;

-- cerrar la cita y probar el cruce de horario con otro estudiante
UPDATE citas SET cit_estado = 'REALIZADA', cit_cerrada_en = now();
SET LOCAL ROLE alertas_owner;
INSERT INTO usuarios (usu_id, usu_ins_id, usu_usuario, usu_contrasena_hash, usu_rol) OVERRIDING SYSTEM VALUE
VALUES (910005, 900001, '1002', 'x', 'ESTUDIANTE');
INSERT INTO estudiantes (est_id, est_ins_id, est_usu_id, est_codigo, est_codigo_qr, est_tipo_doc, est_nro_doc, est_nombres, est_apellidos)
OVERRIDING SYSTEM VALUE VALUES (920003, 900001, 910005, 'estC', 'qrC', 'TI', '1002', 'Laura', 'Rios');
SET LOCAL ROLE alertas_app;
SELECT set_config('app.current_tenant_id', '900001', true);

DO $$
BEGIN
    BEGIN
        INSERT INTO citas (cit_ins_id, cit_codigo, cit_est_id, cit_psi_id, cit_inicio, cit_fin, cit_modalidad, cit_creada_por)
        VALUES (900001, 'citC', 920003, 930001, '2026-10-01 10:30-05', '2026-10-01 11:15-05', 'VIRTUAL', 910003);
        RAISE EXCEPTION 'FALLO: se permitieron citas cruzadas del mismo psicorientador';
    EXCEPTION WHEN exclusion_violation THEN RAISE NOTICE 'OK  sin citas cruzadas';
    END;

    -- relacionar la cita de un estudiante con la alerta de otro
    BEGIN
        INSERT INTO citas (cit_ins_id, cit_codigo, cit_est_id, cit_psi_id, cit_inicio, cit_fin, cit_modalidad, cit_creada_por)
        VALUES (900001, 'citD', 920003, 930001, '2026-10-01 12:00-05', '2026-10-01 12:45-05', 'VIRTUAL', 910003);
        INSERT INTO citas_alertas (cia_ins_id, cia_cit_id, cia_ale_id, cia_est_id)
        SELECT 900001, (SELECT max(cit_id) FROM citas), ale_id, 920003 FROM alertas;
        RAISE EXCEPTION 'FALLO: cita de un estudiante con alerta de otro';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'OK  cita y alerta del mismo estudiante';
    END;

    -- busqueda sin tildes (el dato tiene tildes a proposito)
    ASSERT (SELECT count(*) FROM estudiantes WHERE est_busqueda LIKE '%' || lower(f_unaccent('JOSE PEREZ')) || '%') = 1,
        'FALLO: la busqueda sin tildes no encuentra a Jose Perez';
    RAISE NOTICE 'OK  busqueda sin tildes';
END $$;

-- 4. estadisticas del superadmin: sin tenant la app no ve filas, pero las funciones sa_* si dan los totales
SET LOCAL ROLE alertas_app;
SELECT set_config('app.current_tenant_id', '', true);
DO $$
BEGIN
    ASSERT (SELECT count(*) FROM alertas) = 0, 'FALLO: sin tenant se ven alertas';
    ASSERT (SELECT alertas FROM sa_resumen(NULL, NULL, NULL, 'America/Bogota')) > 0,
        'FALLO: sa_resumen no ve las alertas';
    ASSERT (SELECT sum(total) FROM sa_usuarios_por_rol(NULL)) > 0, 'FALLO: sa_usuarios_por_rol no ve usuarios';
    RAISE NOTICE 'OK  el superadmin ve totales entre colegios sin ver filas';
END $$;

\echo '=== TODAS LAS PRUEBAS PASARON ==='
ROLLBACK;
