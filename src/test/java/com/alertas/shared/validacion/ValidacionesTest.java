package com.alertas.shared.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ValidacionesTest {

    @Test
    void nombresSoloConLetras() {

        assertThat(Validaciones.esNombre("María José")).isTrue();
        assertThat(Validaciones.esNombre("Pérez-Gómez")).isTrue();
        assertThat(Validaciones.esNombre("D'Angelo")).isTrue();
        assertThat(Validaciones.esNombre("  Ana   Lucia ")).isTrue();
        assertThat(Validaciones.esNombre("Begoña")).isTrue();

        assertThat(Validaciones.esNombre("Ana2")).isFalse();
        assertThat(Validaciones.esNombre("Juan_Carlos")).isFalse();
        assertThat(Validaciones.esNombre("Ana.")).isFalse();
        assertThat(Validaciones.esNombre("-Ana")).isFalse();
        assertThat(Validaciones.esNombre("A")).isFalse();
        assertThat(Validaciones.esNombre("123")).isFalse();
    }

    @Test
    void nombresQuedanConMayusculaInicial() {

        assertThat(Formato.nombrePropio("mARIA   jOSE")).isEqualTo("Maria Jose");
        assertThat(Formato.nombrePropio("JUAN DE LA CRUZ")).isEqualTo("Juan de la Cruz");
        assertThat(Formato.nombrePropio("perez-gomez")).isEqualTo("Perez-Gomez");
        assertThat(Formato.nombrePropio("d'angelo")).isEqualTo("D'Angelo");
        assertThat(Formato.nombrePropio("ÁNGELA ÑUSTES")).isEqualTo("Ángela Ñustes");
        assertThat(Formato.nombrePropio("bogota d.c.")).isEqualTo("Bogota D.C.");
        assertThat(Formato.nombrePropio("20 DE JULIO")).isEqualTo("20 de Julio");
        assertThat(Formato.nombrePropio("   ")).isNull();
    }

    @Test
    void correos() {

        assertThat(Validaciones.esCorreo("ana.rios@colegio.edu.co")).isTrue();
        assertThat(Validaciones.esCorreo("ANA+notas@Gmail.com")).isTrue();

        assertThat(Validaciones.esCorreo("ana@gmail")).isFalse();
        assertThat(Validaciones.esCorreo("ana@@gmail.com")).isFalse();
        assertThat(Validaciones.esCorreo("ana..rios@gmail.com")).isFalse();
        assertThat(Validaciones.esCorreo(".ana@gmail.com")).isFalse();
        assertThat(Validaciones.esCorreo("ana rios@gmail.com")).isFalse();
        assertThat(Validaciones.esCorreo("ana@gmail.c")).isFalse();
        assertThat(Validaciones.esCorreo("ana@-gmail.com")).isFalse();
        assertThat(Validaciones.esCorreo("@gmail.com")).isFalse();

        assertThat(Formato.correo("  ANA@Gmail.COM ")).isEqualTo("ana@gmail.com");
    }

    @Test
    void celularesYTelefonos() {

        assertThat(Validaciones.esCelular("3001234567")).isTrue();
        assertThat(Validaciones.esCelular("300 123 4567")).isTrue();
        assertThat(Validaciones.esCelular("+57 300 123 4567")).isTrue();

        assertThat(Validaciones.esCelular("300123456")).isFalse();
        assertThat(Validaciones.esCelular("2001234567")).isFalse();
        assertThat(Validaciones.esCelular("300123456a")).isFalse();

        assertThat(Formato.telefono("+57 300-123-4567")).isEqualTo("3001234567");

        assertThat(Validaciones.esTelefono("6047891234")).isTrue();
        assertThat(Validaciones.esTelefono("7891234")).isTrue();
        assertThat(Validaciones.esTelefono("(604) 789 1234")).isTrue();
        assertThat(Validaciones.esTelefono("123")).isFalse();
        assertThat(Validaciones.esTelefono("ext 123456")).isFalse();
    }

    @Test
    void documentoSegunElTipo() {

        assertThat(Validaciones.problemaDocumento("CC", "1067000111")).isNull();
        assertThat(Validaciones.problemaDocumento("CC", "1.067.000.111")).isNull();
        assertThat(Validaciones.problemaDocumento("TI", "1100001")).isNull();
        assertThat(Validaciones.problemaDocumento("CE", "AB12345")).isNull();

        assertThat(Validaciones.problemaDocumento("CC", "10670A0111")).isNotNull();
        assertThat(Validaciones.problemaDocumento("TI", "12")).isNotNull();
        assertThat(Validaciones.problemaDocumento("CC", "0000000")).isNotNull();
        assertThat(Validaciones.problemaDocumento("CE", "AB-12")).isNull();
        assertThat(Validaciones.problemaDocumento("CE", "AB#12")).isNotNull();
    }

    @Test
    void lugaresYTextos() {

        assertThat(Validaciones.esLugar("Bogotá D.C.")).isTrue();
        assertThat(Validaciones.esLugar("San Andrés, Providencia y Santa Catalina")).isTrue();
        assertThat(Validaciones.esLugar("Monteria 2")).isFalse();

        assertThat(Validaciones.esDireccion("Calle 10 # 5-20 apto 301")).isTrue();
        assertThat(Validaciones.esDireccion("Vereda La Esperanza")).isTrue();
        assertThat(Validaciones.esDireccion("12345")).isFalse();
        assertThat(Validaciones.esDireccion("Calle <b>")).isFalse();

        assertThat(Validaciones.esTextoBasico("Nueva EPS")).isTrue();
        assertThat(Validaciones.esTextoBasico("I.E. San José")).isTrue();
        assertThat(Validaciones.esTextoBasico("...")).isFalse();

        assertThat(Validaciones.esCodigoDane("123001000123")).isTrue();
        assertThat(Validaciones.esCodigoDane("12300100012")).isFalse();

        assertThat(Validaciones.tieneLetra("..........")).isFalse();
        assertThat(Validaciones.tieneLetra("1111111111")).isFalse();
        assertThat(Validaciones.tieneLetra("No vino")).isTrue();
    }

    @Test
    void contrasenas() {

        assertThat(Validaciones.esContrasenaSegura("clave2026")).isTrue();
        assertThat(Validaciones.esContrasenaSegura("12345678")).isFalse();
        assertThat(Validaciones.esContrasenaSegura("solo-letras")).isFalse();
    }
}
