package ServidorMulti;
import java.io.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.BiFunction;

class EstadisticasJugador {
    int puntos;
    int victorias;
    int empates;
    int derrotas;

    public EstadisticasJugador(int puntos, int victorias, int empates, int derrotas) {
        this.puntos = puntos;
        this.victorias = victorias;
        this.empates = empates;
        this.derrotas = derrotas;
    }

    public int getTotalPartidas() {
        return victorias + empates + derrotas;
    }
}

public class ManejadorRanking {
    private static final String ARCHIVO_RANKING = "ranking.txt";
    public static Map<String, EstadisticasJugador> ranking = new HashMap<>();

    public static void cargarRanking() {
        ranking.clear();
        try (BufferedReader lector = new BufferedReader(new FileReader(ARCHIVO_RANKING))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                String[] partes = linea.split("-");
                if (partes.length == 5) {
                    try {
                        String usuario = partes[0].trim();
                        int puntos = Integer.parseInt(partes[1].trim());
                        int victorias = Integer.parseInt(partes[2].trim());
                        int empates = Integer.parseInt(partes[3].trim());
                        int derrotas = Integer.parseInt(partes[4].trim());
                        ranking.put(usuario, new EstadisticasJugador(puntos, victorias, empates, derrotas));
                    } catch (NumberFormatException e) {
                        System.err.println("Error de formato en línea de ranking: " + linea);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Archivo de ranking no encontrado. Se creará uno nuevo.");
        } catch (IOException e) {
            System.err.println("Error al cargar ranking: " + e.getMessage());
        }
    }

    public static void guardarRanking() {
        try (BufferedWriter escritor = new BufferedWriter(new FileWriter(ARCHIVO_RANKING))) {
            for (Map.Entry<String, EstadisticasJugador> entry : ranking.entrySet()) {
                EstadisticasJugador stats = entry.getValue();
                String linea = String.format("%s-%d-%d-%d-%d",
                        entry.getKey(), stats.puntos, stats.victorias, stats.empates, stats.derrotas);
                escritor.write(linea);
                escritor.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error al guardar ranking: " + e.getMessage());
        }
    }

    public static EstadisticasJugador getEstadisticas(String usuario) {
        return ranking.computeIfAbsent(usuario, k -> new EstadisticasJugador(0, 0, 0, 0));
    }

    public static void actualizarEstadisticas(String usuarioGanador, String usuarioPerdedor, boolean esEmpate) {
        if (usuarioGanador.startsWith("Invitado") || usuarioPerdedor.startsWith("Invitado")) {
            return;
        }

        if (esEmpate) {
            EstadisticasJugador stats1 = getEstadisticas(usuarioGanador);
            stats1.puntos += 1;
            stats1.empates += 1;

            EstadisticasJugador stats2 = getEstadisticas(usuarioPerdedor);
            stats2.puntos += 1;
            stats2.empates += 1;

        } else {
            EstadisticasJugador statsGanador = getEstadisticas(usuarioGanador);
            statsGanador.puntos += 2;
            statsGanador.victorias += 1;

            EstadisticasJugador statsPerdedor = getEstadisticas(usuarioPerdedor);
            statsPerdedor.derrotas += 1;
        }

        guardarRanking();
    }

    public static String getRankingGeneral() {
        Map<String, EstadisticasJugador> rankingOrdenado = ranking.entrySet().stream()
                .filter(e -> e.getValue().getTotalPartidas() > 0)
                .sorted(Map.Entry.<String, EstadisticasJugador>comparingByValue(
                        Comparator.comparingInt((EstadisticasJugador s) -> s.puntos)
                                .reversed()
                                .thenComparing(Comparator.comparingInt((EstadisticasJugador s) -> s.victorias).reversed())
                ))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        if (rankingOrdenado.isEmpty()) {
            return "No hay partidas registradas para generar un ranking.";
        }

        StringBuilder sb = new StringBuilder("\n--- RANKING GENERAL (Puntos / V / E / D) ---\n");
        int pos = 1;
        for (Map.Entry<String, EstadisticasJugador> entry : rankingOrdenado.entrySet()) {
            EstadisticasJugador stats = entry.getValue();
            sb.append(String.format("%d. %s: %d / %d / %d / %d\n",
                    pos++, entry.getKey(), stats.puntos, stats.victorias, stats.empates, stats.derrotas));
        }
        sb.append("--------------------------------------------\n");
        return sb.toString();
    }

    public static String getEstadisticasVs(String usuario1, String usuario2) {
        BiFunction<Integer, Integer, String> getPercentage = (count, total) -> total > 0 ?
                String.format(" (%.2f%%)", (count / (double) total) * 100) : " (0.00%)";

        EstadisticasJugador stats1 = getEstadisticas(usuario1);
        EstadisticasJugador stats2 = getEstadisticas(usuario2);

        int totalPartidas1 = stats1.getTotalPartidas();
        int totalPartidas2 = stats2.getTotalPartidas();

        if (totalPartidas1 == 0 && totalPartidas2 == 0) {
            return String.format("Ninguno de los usuarios (%s y %s) tiene partidas registradas.", usuario1, usuario2);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n========================================================\n");
        sb.append(String.format("          ESTADÍSTICAS COMPARADAS (%s vs %s)    \n", usuario1, usuario2));
        sb.append("========================================================\n");

        sb.append(String.format("%-20s | %20s | %20s\n", "MÉTRICA", usuario1, usuario2));
        sb.append("---------------------|----------------------|----------------------\n");

        sb.append(String.format("%-20s | %20d | %20d\n", "Puntos Totales", stats1.puntos, stats2.puntos));
        sb.append(String.format("%-20s | %20d | %20d\n", "Total Partidas", totalPartidas1, totalPartidas2));
        sb.append("---------------------|----------------------|----------------------\n");

        sb.append(String.format("%-20s | %20s | %20s\n", "Victorias (2 pts)",
                stats1.victorias + getPercentage.apply(stats1.victorias, totalPartidas1),
                stats2.victorias + getPercentage.apply(stats2.victorias, totalPartidas2)
        ));
        sb.append(String.format("%-20s | %20s | %20s\n", "Empates (1 pt)",
                stats1.empates + getPercentage.apply(stats1.empates, totalPartidas1),
                stats2.empates + getPercentage.apply(stats2.empates, totalPartidas2)
        ));
        sb.append(String.format("%-20s | %20s | %20s\n", "Derrotas (0 pts)",
                stats1.derrotas + getPercentage.apply(stats1.derrotas, totalPartidas1),
                stats2.derrotas + getPercentage.apply(stats2.derrotas, totalPartidas2)
        ));

        sb.append("========================================================\n");
        return sb.toString();
    }
}
