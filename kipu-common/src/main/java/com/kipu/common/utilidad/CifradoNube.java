/*Copyright (c) 2026 Kipu. Todos los derechos reservados.
 * Licenciado bajo la Licencia de Uso de Software Kipu (basada en Elastic License 2.0).
 * Consulte el archivo LICENSE en la raiz del proyecto para mas informacion.
 * Queda prohibido el uso, copia o distribucion sin autorizacion expresa del titular.*/
package com.kipu.common.utilidad;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

/**
 * Cifrado AES-256-GCM para proteger strings sensibles (ej. Atlas URI) en el JAR.
 * <p>
 * Flujo: en build, Maven ejecuta {@link #main(String[])} para cifrar la URI en {@code cloud.dat}.
 * En runtime, {@link #descifrar(byte[])} la recupera en memoria.
 * <p>
 * La clave se deriva de constantes internas con PBKDF2 (65 536 iteraciones).
 * No es criptografía irrompible (las constantes están en bytecode), pero impide
 * la extracción trivial con {@code strings} o {@code ps aux}.
 */
public final class CifradoNube {

    private CifradoNube() {}

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int CLAVE_BITS = 256;
    private static final int ITERACIONES_PBKDF2 = 65_536;

    // Sal fija para derivación — cambiar estos bytes invalida todos los cloud.dat anteriores
    private static final byte[] SAL = {
        (byte) 0xB4, (byte) 0xA7, (byte) 0x1C, (byte) 0x3E, (byte) 0x5F, (byte) 0x88, (byte) 0x02, (byte) 0xD9,
        (byte) 0x6B, (byte) 0xF1, (byte) 0x44, (byte) 0x70, (byte) 0xAC, (byte) 0x2D, (byte) 0xE6, (byte) 0x93,
        (byte) 0x17, (byte) 0x5A, (byte) 0xCF, (byte) 0x81, (byte) 0x3B, (byte) 0x64, (byte) 0xD2, (byte) 0x0E,
        (byte) 0x9F, (byte) 0x48, (byte) 0xA3, (byte) 0x76, (byte) 0x25, (byte) 0xBC, (byte) 0x51, (byte) 0xE8
    };

    // Semilla de derivación — string no obvio que se combina con la sal
    private static final String SEMILLA = "bx:f7c9a2e0-4d81-4b37-9e6c-3a512f8d0ba7:nube";

    // ─── API PÚBLICA ───────────────────────────────────────────────────

    /** Cifra un texto plano. Retorna IV (12 bytes) + ciphertext+tag. */
    public static byte[] cifrar(String textoPlano) {
        try {
            SecretKey clave = derivarClave();
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, iv));

            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));

            // IV || ciphertext+tag
            byte[] resultado = new byte[IV_BYTES + cifrado.length];
            System.arraycopy(iv, 0, resultado, 0, IV_BYTES);
            System.arraycopy(cifrado, 0, resultado, IV_BYTES, cifrado.length);
            return resultado;
        } catch (Exception e) {
            throw new IllegalStateException("Error cifrando datos de nube", e);
        }
    }

    /** Descifra datos producidos por {@link #cifrar(String)}. */
    public static String descifrar(byte[] datos) {
        try {
            if (datos == null || datos.length <= IV_BYTES) {
                return "";
            }

            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(datos, 0, iv, 0, IV_BYTES);

            byte[] cifrado = new byte[datos.length - IV_BYTES];
            System.arraycopy(datos, IV_BYTES, cifrado, 0, cifrado.length);

            SecretKey clave = derivarClave();
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, iv));

            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Error descifrando datos de nube", e);
        }
    }

    /**
     * Lee {@code cloud.dat} desde el classpath y lo descifra.
     * @return URI descifrada, o cadena vacía si el recurso no existe o está vacío
     */
    public static String leerDesdeClasspath() {
        try (InputStream is = CifradoNube.class.getResourceAsStream("/cloud.dat")) {
            if (is == null) return "";
            byte[] datos = is.readAllBytes();
            if (datos.length == 0) return "";
            return descifrar(datos);
        } catch (IOException e) {
            return "";
        }
    }

    // ─── CLI PARA BUILD ────────────────────────────────────────────────

    /**
     * Punto de entrada para Maven exec-maven-plugin.
     * <pre>
     *   args[0] = texto a cifrar (URI). Si vacío/null → escribe 0 bytes.
     *   args[1] = ruta destino del archivo cifrado (cloud.dat).
     * </pre>
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Uso: CifradoNube <texto> <ruta-destino>");
            System.exit(1);
        }

        String texto = args[0];
        Path destino = Path.of(args[1]);

        try {
            Files.createDirectories(destino.getParent());

            if (texto == null || texto.isBlank() || "${env.KIPU_ATLAS_URI}".equals(texto)) {
                // Sin URI → archivo vacío (cloud deshabilitado)
                Files.write(destino, new byte[0]);
                System.out.println("[CifradoNube] cloud.dat vacio (sin URI de nube)");
            } else {
                byte[] cifrado = cifrar(texto);
                Files.write(destino, cifrado);
                System.out.println("[CifradoNube] cloud.dat generado (" + cifrado.length + " bytes cifrados)");
            }
        } catch (Exception e) {
            System.err.println("[CifradoNube] Error: " + e.getMessage());
            System.exit(1);
        }
    }

    // ─── DERIVACIÓN DE CLAVE ───────────────────────────────────────────

    private static SecretKey derivarClave() throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
                SEMILLA.toCharArray(), SAL, ITERACIONES_PBKDF2, CLAVE_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] claveBytes = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(claveBytes, "AES");
    }
}
