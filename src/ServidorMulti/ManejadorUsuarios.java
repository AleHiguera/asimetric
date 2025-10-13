package ServidorMulti;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class ManejadorUsuarios {
    private static final String ARCHIVO_USUARIOS = "usuarios.txt";

    public static Map<String, String> cargarUsuarios() {
        Map<String, String> usuarios = new HashMap<>();
        try (BufferedReader lector = new BufferedReader(new FileReader(ARCHIVO_USUARIOS))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                String[] partes = linea.split(":");
                if (partes.length == 2) {
                    usuarios.put(partes[0].trim(), partes[1].trim());
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Archivo de usuarios no encontrado. Se creará uno nuevo al registrar el primer usuario.");
        } catch (IOException e) {
            System.err.println("Error al cargar usuarios: " + e.getMessage());
        }
        return usuarios;
    }

    // Guarda un nuevo usuario en el archivo
    public static boolean registrarUsuario(String nombreUsuario, String contrasena) {
        if (nombreUsuario == null || contrasena == null || nombreUsuario.trim().isEmpty() || contrasena.trim().isEmpty()) {
            return false;
        }

        if (ServidorMulti.usuariosRegistrados.containsKey(nombreUsuario)) {
            return false;
        }

        try (BufferedWriter escritor = new BufferedWriter(new FileWriter(ARCHIVO_USUARIOS, true))) {
            escritor.write(nombreUsuario + ":" + contrasena);
            escritor.newLine();

            ServidorMulti.usuariosRegistrados.put(nombreUsuario, contrasena);
            return true;
        } catch (IOException e) {
            System.err.println("Error al registrar usuario: " + e.getMessage());
            return false;
        }
    }

    public static boolean verificarCredenciales(String nombreUsuario, String contrasena) {
        String contrasenaGuardada = ServidorMulti.usuariosRegistrados.get(nombreUsuario);
        return contrasenaGuardada != null && contrasenaGuardada.equals(contrasena);
    }
}