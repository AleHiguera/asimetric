package ServidorMulti.Juego;
import ServidorMulti.ClienteAuthManager;
import ServidorMulti.SQLiteManager;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RankingManager {
    private static Map<String, RankingData> stats = new ConcurrentHashMap<>();
    private static final int PUNTOS_VICTORIA = 2;
    private static final int PUNTOS_EMPATE = 1;
    private static final int PUNTOS_DERROTA = 0;

    static {
        cargarEstadisticasDesdeDB();
    }

    private RankingManager() {}
    private static void cargarEstadisticasDesdeDB() {
        String sql = "SELECT usuario, puntos, victorias, derrotas, empates FROM Rankings";
        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String usuario = rs.getString("usuario");
                RankingData data = new RankingData(usuario);
                data.setPuntos(rs.getInt("puntos"));
                data.setVictorias(rs.getInt("victorias"));
                data.setDerrotas(rs.getInt("derrotas"));
                data.setEmpates(rs.getInt("empates"));
                stats.put(usuario, data);
            }
            System.out.println("[DB] Ranking cargado desde SQLite. Total: " + stats.size() + " usuarios.");
        } catch (SQLException e) {
            System.err.println("[DB] Error al cargar ranking desde DB: " + e.getMessage());
        }
    }
    private static void guardarEstadisticas(RankingData data) throws IOException {
        String sql = "INSERT OR REPLACE INTO Rankings (usuario, puntos, victorias, derrotas, empates) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, data.getNombreUsuario());
            pstmt.setInt(2, data.getPuntos());
            pstmt.setInt(3, data.getVictorias());
            pstmt.setInt(4, data.getDerrotas());
            pstmt.setInt(5, data.getEmpates());
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[DB] Error al guardar ranking en DB: " + e.getMessage());
            // Se lanza IOException para manejar el error de persistencia en el flujo de juego.
            throw new IOException("Fallo al guardar ranking en DB", e);
        }
    }
    private static RankingData obtenerOCrearEstadisticas(String nombre) {
        return stats.computeIfAbsent(nombre, RankingData::new);
    }

    public static void actualizarEstadisticas(String ganador, String perdedor) throws IOException {
        RankingData dataGanador = obtenerOCrearEstadisticas(ganador);
        RankingData dataPerdedor = obtenerOCrearEstadisticas(perdedor);

        dataGanador.registrarVictoria(PUNTOS_VICTORIA);
        dataPerdedor.registrarDerrota(PUNTOS_DERROTA);

        // Persistencia de ambos usuarios en la DB
        guardarEstadisticas(dataGanador);
        guardarEstadisticas(dataPerdedor);
    }

    public static void actualizarEstadisticasEmpate(String j1, String j2) throws IOException {
        RankingData dataJ1 = obtenerOCrearEstadisticas(j1);
        RankingData dataJ2 = obtenerOCrearEstadisticas(j2);

        dataJ1.registrarEmpate(PUNTOS_EMPATE);
        dataJ2.registrarEmpate(PUNTOS_EMPATE);

        // Persistencia de ambos usuarios en la DB
        guardarEstadisticas(dataJ1);
        guardarEstadisticas(dataJ2);
    }

    public static String generarRankingGeneral(String jugadorActual) {
        List<RankingData> topPlayers = stats.values().stream()
                .sorted(Comparator.comparingInt(RankingData::getPuntos).reversed())
                .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder();
        sb.append("--- RANKING GENERAL (Top 10) ---\n");
        sb.append(String.format("%-4s %-15s %-6s %-6s %-6s %s\n", "Pos", "Usuario", "Pts", "Gan", "Per", "Emp"));
        for (int i = 0; i < Math.min(10, topPlayers.size()); i++) {
            RankingData data = topPlayers.get(i);
            sb.append(String.format("%-4d %-15s %-6d %-6d %-6d %d\n",
                    i + 1, data.getNombreUsuario(), data.getPuntos(), data.getVictorias(), data.getDerrotas(), data.getEmpates()));
        }
        return sb.toString();
    }

    public static String generarRankingPersonal(String nombre) {
        RankingData data = obtenerOCrearEstadisticas(nombre);
        StringBuilder sb = new StringBuilder();
        sb.append("--- TUS ESTADÍSTICAS ---\n");
        sb.append(String.format("%-15s %-6s %-6s %-6s %-6s\n", "Usuario", "Pts", "Gan", "Per", "Emp"));
        sb.append(String.format("%-15s %-6d %-6d %-6d %-6d\n",
                data.getNombreUsuario(), data.getPuntos(), data.getVictorias(), data.getDerrotas(), data.getEmpates()));
        return sb.toString();
    }

    public static String generarRankingDuelo(String j1, String j2) {
        RankingData dataJ1 = obtenerOCrearEstadisticas(j1);
        RankingData dataJ2 = obtenerOCrearEstadisticas(j2);
        if (!ClienteAuthManager.existeUsuario(j1) || !ClienteAuthManager.existeUsuario(j2)) {
            return "[ERROR RANKING] Uno o ambos usuarios no existen en el sistema.";
        }
        return formatDueloStats(dataJ1, dataJ2);
    }

    private static String formatDueloStats(RankingData d1, RankingData d2) {
        int v1 = d1.getVictorias();
        int d1_perdidas = d1.getDerrotas();
        int e1 = d1.getEmpates();
        int v2 = d2.getVictorias();
        int d2_perdidas = d2.getDerrotas();
        int e2 = d2.getEmpates();
        int total = v1 + d1_perdidas + e1 + v2 + d2_perdidas + e2;
        if (total == 0) return String.format("--- DUELO: %s vs %s ---\n[DUELO] Nunca han jugado partidas registradas.", d1.getNombreUsuario(), d2.getNombreUsuario());
        double winRate1 = (v1 + d2_perdidas) * 100.0 / total;
        double winRate2 = (v2 + d1_perdidas) * 100.0 / total;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("--- DUELO: %s vs %s ---\n", d1.getNombreUsuario(), d2.getNombreUsuario()));
        sb.append(String.format("Partidas totales registradas: %d\n\n", total));
        sb.append(String.format("%-15s | %-8s | %-8s | %-8s\n", "Estadística", d1.getNombreUsuario(), d2.getNombreUsuario(), "% Total"));
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("%-15s | %-8d | %-8d | %-8.1f%%\n", "Victorias", v1, v2, winRate1));
        sb.append(String.format("%-15s | %-8d | %-8d | %-8.1f%%\n", "Derrotas", d1_perdidas, d2_perdidas, winRate2));
        sb.append(String.format("%-15s | %-8d | %-8d |\n", "Empates", e1, e2));
        sb.append(String.format("%-15s | %-8d | %-8d |\n", "Puntos", d1.getPuntos(), d2.getPuntos()));
        return sb.toString();
    }
}