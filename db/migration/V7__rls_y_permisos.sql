-- V7: Row Level Security y permisos del rol de la aplicación.
--
-- Las políticas se aplican a alertas_app porque no es dueño de las tablas. No se usa
-- FORCE ROW LEVEL SECURITY: así las funciones SECURITY DEFINER del dueño (estadísticas
-- globales del superadmin, fase 14) pueden devolver agregados de todas las instituciones.
--
-- USING filtra lo que se lee, actualiza y borra; WITH CHECK impide insertar o mover
-- una fila a otra institución.

DO $$
DECLARE
    t record;
BEGIN
    FOR t IN
        SELECT * FROM (VALUES
            ('usuarios',          'usu_ins_id'),
            ('personal',          'per_ins_id'),
            ('estudiantes',       'est_ins_id'),
            ('familiares',        'fam_ins_id'),
            ('anios_lectivos',    'anl_ins_id'),
            ('grados',            'gra_ins_id'),
            ('grupos',            'grp_ins_id'),
            ('ubicaciones',       'ubi_ins_id'),
            ('categorias_alerta', 'cat_ins_id'),
            ('alertas',           'ale_ins_id'),
            ('citas',             'cit_ins_id'),
            ('citas_alertas',     'cia_ins_id'),
            ('notificaciones',    'not_ins_id'),
            ('sms_envios',        'sms_ins_id'),
            ('bitacora',          'bit_ins_id')
        ) AS v(tabla, columna)
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t.tabla);
        EXECUTE format(
            'CREATE POLICY p_%s_tenant ON %I USING (%I = tenant_actual()) WITH CHECK (%I = tenant_actual())',
            t.tabla, t.tabla, t.columna, t.columna);
    END LOOP;
END $$;


-- Permisos mínimos por tabla. Solo se concede DELETE donde el negocio borra de verdad;
-- el resto se inactiva. La bitácora es de solo inserción.
GRANT SELECT, INSERT, UPDATE ON instituciones, superadministradores TO alertas_app;

GRANT SELECT, INSERT, UPDATE ON
    usuarios, personal, estudiantes, anios_lectivos, grados,
    categorias_alerta, alertas, citas, sms_envios
TO alertas_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    familiares, grupos, ubicaciones, citas_alertas, notificaciones
TO alertas_app;

GRANT SELECT, INSERT ON bitacora TO alertas_app;

-- Las columnas identity usan secuencias internas
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO alertas_app;
