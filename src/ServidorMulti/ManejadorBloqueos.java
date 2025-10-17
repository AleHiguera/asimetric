package ServidorMulti;
import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ManejadorBloqueos {
    private static final String ARCHIVO_BLOQUEOS = "bloqueados.txt";
    private static Map<String, Set<String>> listaBloqueos = new HashMap<>();

    public static void cargarBloqueos() {
        listaBloqueos.clear();
        try (BufferedReader lector = new BufferedReader(new FileReader(ARCHIVO_BLOQUEOS))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                String[] partes = linea.split(":", 2);
                if (partes.length == 2) {
                    String usuario = partes[0].trim();
                    String[] bloqueadosArray = partes[1].split(",");
                    Set<String> bloqueadosSet = new HashSet<>();
                    for (String b : bloqueadosArray) {
                        String nombreLimpio = b.trim();
                        if (!nombreLimpio.isEmpty()) {
                            bloqueadosSet.add(nombreLimpio);
                        }
                    }
                    if (!bloqueadosSet.isEmpty()) {
                        listaBloqueos.put(usuario, bloqueadosSet);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Archivo de bloqueos no encontrado. Se creará uno nuevo si es necesario.");
        } catch (IOException e) {
            System.err.println("Error al cargar bloqueos: " + e.getMessage());
        }
    }

    private static void guardarBloqueos() {
        try (BufferedWriter escritor = new BufferedWriter(new FileWriter(ARCHIVO_BLOQUEOS))) {
            for (Map.Entry<String, Set<String>> entry : listaBloqueos.entrySet()) {
                String usuario = entry.getKey();
                String listaBloqueadosStr = String.join(",", entry.getValue());
                escritor.write(usuario + ":" + listaBloqueadosStr);
                escritor.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error al guardar bloqueos: " + e.getMessage());
        }
    }

    public static boolean esUsuarioBloqueado(String remitente, String destinatario) {
        Set<String> bloqueados = listaBloqueos.get(destinatario);
        return bloqueados != null && bloqueados.contains(remitente);
    }

    public static boolean estaBloqueadoPor(String bloqueador, String bloqueado) {
        Set<String> bloqueados = listaBloqueos.get(bloqueador);
        return bloqueados != null && bloqueados.contains(bloqueado);
    }

    public static boolean bloquearUsuario(String bloqueador, String aBloquear) {
        Set<String> bloqueados = listaBloqueos.computeIfAbsent(bloqueador, k -> new HashSet<>());

        if (bloqueados.add(aBloquear)) {
            guardarBloqueos();
            return true;
        }
        return false;
    }

    public static boolean desbloquearUsuario(String desbloqueador, String aDesbloquear) {
        Set<String> bloqueados = listaBloqueos.get(desbloqueador);

        if (bloqueados != null && bloqueados.remove(aDesbloquear)) {
            if (bloqueados.isEmpty()) {
                listaBloqueos.remove(desbloqueador);
            }
            guardarBloqueos();
            return true;
        }
        return false;
    }
}