-- la fecha de nacimiento se usa para la edad y las estadisticas por rango de edad.
-- el backend exige entre 3 y 25 anios al registrarla; aqui solo un tope para que nada absurdo (ej. 1600) llegue a la base
ALTER TABLE estudiantes ADD CONSTRAINT ck_estudiantes_fecha_nacimiento
    CHECK (est_fecha_nacimiento IS NULL
           OR (est_fecha_nacimiento >= DATE '1950-01-01' AND est_fecha_nacimiento < DATE '2100-01-01'));
