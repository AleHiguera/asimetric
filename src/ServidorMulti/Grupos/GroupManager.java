package ServidorMulti.Grupos;
import ServidorMulti.ClienteAuthManager;
import ServidorMulti.ClienteManager;
import ServidorMulti.UnCliente;
import ServidorMulti.SQLiteManager;
import ServidorMulti.BlockListManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;

public class GroupManager {
    private static Map<String, Grupo> grupos = new ConcurrentHashMap<>();
    private static final Map<String, String> grupoActualUsuario = new ConcurrentHashMap<>();

    static {
        cargarGruposDesdeDB();
        inicializarGrupoTodos();
    }

    private GroupManager() {}

    private static void cargarGruposDesdeDB() {
        String sql = "SELECT nombre, creador FROM Grupos";
        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String nombre = rs.getString("nombre");
                String creador = rs.getString("creador");
                grupos.put(nombre, new Grupo(nombre, creador));
            }
            System.out.println("[DB] Grupos cargados desde SQLite.");
        } catch (SQLException e) {
            System.err.println("[DB] Error al cargar grupos: " + e.getMessage());
        }
    }

    private static void inicializarGrupoTodos() {
        if (!grupos.containsKey("Todos")) {
            String nombre = "Todos";
            String creador = "SYSTEM";
            String sqlGrupo = "INSERT OR IGNORE INTO Grupos (nombre, creador) VALUES (?, ?)";

            try (Connection conn = SQLiteManager.getConnection();
                 PreparedStatement pstmtGrupo = conn.prepareStatement(sqlGrupo)) {

                pstmtGrupo.setString(1, nombre);
                pstmtGrupo.setString(2, creador);
                pstmtGrupo.executeUpdate();
                grupos.put(nombre, new Grupo(nombre, creador));

            } catch (SQLException e) {
                System.err.println("[DB] Error al inicializar Grupo 'Todos': " + e.getMessage());
            }
        }
    }

    public static void asignarGrupoInicial(String nombreUsuario, boolean esInvitado) {
        if (!grupoActualUsuario.containsKey(nombreUsuario)) {
            unirGrupo(nombreUsuario, "Todos");
        }

        if (esInvitado && grupoActualUsuario.containsKey(nombreUsuario) && !grupoActualUsuario.get(nombreUsuario).equals("Todos")) {
            unirGrupo(nombreUsuario, "Todos");
        }
    }

    public static void removerMiembroDeTodos(String nombreUsuario) {
        String sqlDelete = "DELETE FROM GrupoMiembros WHERE usuario = ?";
        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmtDelete = conn.prepareStatement(sqlDelete)) {

            pstmtDelete.setString(1, nombreUsuario);
            pstmtDelete.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[DB] Error al remover miembro de grupos en DB: " + e.getMessage());
        }
        grupoActualUsuario.remove(nombreUsuario);
    }

    public static String crearGrupo(String nombreGrupo, String nombreCreador) throws IOException {
        if (nombreGrupo.equalsIgnoreCase("Todos")) {
            return "[ERROR GRUPO] El nombre 'Todos' está reservado.";
        }
        if (grupos.containsKey(nombreGrupo)) {
            return "[ERROR GRUPO] El grupo '" + nombreGrupo + "' ya existe.";
        }
        if (!ClienteAuthManager.existeUsuario(nombreCreador)) {
            return "[ERROR GRUPO] Debes ser un usuario registrado para crear grupos.";
        }

        String sqlGrupo = "INSERT INTO Grupos (nombre, creador) VALUES (?, ?)";
        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmtGrupo = conn.prepareStatement(sqlGrupo)) {

            pstmtGrupo.setString(1, nombreGrupo);
            pstmtGrupo.setString(2, nombreCreador);
            pstmtGrupo.executeUpdate();

            Grupo nuevoGrupo = new Grupo(nombreGrupo, nombreCreador);
            grupos.put(nombreGrupo, nuevoGrupo);
            unirGrupo(nombreCreador, nombreGrupo);

            return String.format("[INFO GRUPO] Grupo '%s' creado y te has unido.", nombreGrupo);

        } catch (SQLException e) {
            System.err.println("[DB] Error al crear grupo: " + e.getMessage());
            return "[ERROR GRUPO] Fallo interno al crear el grupo.";
        }
    }

    public static String borrarGrupo(String nombreGrupo, String nombreUsuario) throws IOException {
        if (nombreGrupo.equalsIgnoreCase("Todos")) {
            return "[ERROR GRUPO] El grupo 'Todos' no puede ser eliminado.";
        }
        Grupo grupo = grupos.get(nombreGrupo);
        if (grupo == null) {
            return "[ERROR GRUPO] El grupo '" + nombreGrupo + "' no existe.";
        }
        if (!grupo.esCreador(nombreUsuario)) {
            return "[ERROR GRUPO] Solo el creador (" + grupo.getCreador() + ") puede borrar el grupo.";
        }

        obtenerMiembrosGrupo(nombreGrupo).forEach(miembro -> unirGrupo(miembro, "Todos"));

        String sqlDeleteGrupo = "DELETE FROM Grupos WHERE nombre = ?";
        String sqlDeleteMiembros = "DELETE FROM GrupoMiembros WHERE nombre_grupo = ?";

        Connection conn = null;

        try {
            conn = SQLiteManager.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement pstmtMiembros = conn.prepareStatement(sqlDeleteMiembros);
                 PreparedStatement pstmtGrupo = conn.prepareStatement(sqlDeleteGrupo)) {

                pstmtMiembros.setString(1, nombreGrupo);
                pstmtMiembros.executeUpdate();

                pstmtGrupo.setString(1, nombreGrupo);
                pstmtGrupo.executeUpdate();

                conn.commit();
                grupos.remove(nombreGrupo);
                return String.format("[INFO GRUPO] Grupo '%s' eliminado con éxito.", nombreGrupo);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error al borrar grupo: " + e.getMessage());
            return "[ERROR GRUPO] Fallo interno al borrar el grupo.";
        } finally {
            try {
                if (conn != null) conn.close();
            } catch (SQLException ex) {
                System.err.println("[DB] Error al cerrar conexión después de borrar: " + ex.getMessage());
            }
        }
    }

    public static String unirGrupo(String nombreUsuario, String nombreGrupo) {
        Grupo grupoDestino = grupos.get(nombreGrupo);
        if (grupoDestino == null) {
            return "[ERROR GRUPO] El grupo '" + nombreGrupo + "' no existe.";
        }

        boolean esInvitado = !ClienteAuthManager.existeUsuario(nombreUsuario);
        if (esInvitado && !nombreGrupo.equalsIgnoreCase("Todos")) {
            return "[ERROR GRUPO] Los invitados solo pueden estar en el grupo 'Todos'.";
        }

        String grupoAnterior = grupoActualUsuario.get(nombreUsuario);

        if (grupoAnterior != null && grupoAnterior.equals(nombreGrupo)) {
            return "[INFO GRUPO] Ya te encuentras en el grupo '" + nombreGrupo + "'.";
        }

        String sqlInsert = "INSERT OR IGNORE INTO GrupoMiembros (nombre_grupo, usuario) VALUES (?, ?)";
        String sqlDelete = "DELETE FROM GrupoMiembros WHERE nombre_grupo = ? AND usuario = ?";

        Connection conn = null;

        try {
            conn = SQLiteManager.getConnection();
            conn.setAutoCommit(false);

            if (grupoAnterior != null) {
                try (PreparedStatement pstmtDelete = conn.prepareStatement(sqlDelete)) {
                    pstmtDelete.setString(1, grupoAnterior);
                    pstmtDelete.setString(2, nombreUsuario);
                    pstmtDelete.executeUpdate();
                }
            }

            try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert)) {
                pstmtInsert.setString(1, nombreGrupo);
                pstmtInsert.setString(2, nombreUsuario);
                pstmtInsert.executeUpdate();
            }

            conn.commit();
            grupoActualUsuario.put(nombreUsuario, nombreGrupo);

            try {
                UnCliente cliente = ClienteManager.obtenerClientePorNombre(nombreUsuario);
                if (cliente != null) {
                    cliente.enviarMensaje(String.format("[INFO GRUPO] Te has unido a '%s'.", nombreGrupo));
                    cliente.enviarMensaje(obtenerHistorialGrupo(nombreGrupo));
                }
            } catch (IOException e) {
                System.err.println("Error I/O al notificar cambio de grupo: " + e.getMessage());
            }

        } catch (SQLException e) {
            System.err.println("[DB] Error al unir grupo: " + e.getMessage());

            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                System.err.println("[DB] Error al hacer rollback: " + ex.getMessage());
            }

            return "[ERROR GRUPO] Fallo interno al unirte al grupo.";
        } finally {
            try {
                if (conn != null) conn.close();
            } catch (SQLException ex) {
                System.err.println("[DB] Error al cerrar conexión después de unir: " + ex.getMessage());
            }
        }
        return String.format("[INFO GRUPO] Grupo cambiado a '%s'.", nombreGrupo);
    }

    public static String obtenerNombreGrupoActual(String nombreUsuario) {
        return grupoActualUsuario.getOrDefault(nombreUsuario, "Todos");
    }

    public static void enviarMensajeGrupo(String remitente, String mensaje) throws IOException {
        String nombreGrupo = obtenerNombreGrupoActual(remitente);

        if (!grupos.containsKey(nombreGrupo)) return;

        String mensajeCompleto = String.format("[%s] [%s]: %s", nombreGrupo, remitente, mensaje);
        String sql = "INSERT INTO HistorialMensajes (nombre_grupo, mensaje) VALUES (?, ?)";

        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, nombreGrupo);
            pstmt.setString(2, mensajeCompleto);
            pstmt.executeUpdate();

            Set<String> miembros = obtenerMiembrosGrupo(nombreGrupo);
            for (String receptor : miembros) {
                if (receptor.equals(remitente)) {
                    continue;
                }

                UnCliente clienteReceptor = ClienteManager.obtenerClientePorNombre(receptor);

                boolean estaBloqueadoPorReceptor = BlockListManager.estaBloqueado(receptor, remitente);
                boolean elReceptorEstaBloqueado = BlockListManager.estaBloqueado(remitente, receptor);

                if (clienteReceptor != null && !estaBloqueadoPorReceptor && !elReceptorEstaBloqueado) {
                    clienteReceptor.enviarMensaje(mensajeCompleto);
                }
            }

        } catch (SQLException e) {
            System.err.println("[DB] Error al guardar mensaje: " + e.getMessage());
        }
    }

    public static String obtenerListadoGrupos() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- GRUPOS DISPONIBLES ---\n");
        grupos.keySet().stream()
                .sorted()
                .forEach(nombre -> {
                    Grupo g = grupos.get(nombre);
                    int numMiembros = obtenerMiembrosGrupo(nombre).size();
                    sb.append(String.format(" -> %s (Creador: %s, Miembros: %d)\n",
                            nombre, g.getCreador(), numMiembros));
                });
        return sb.toString();
    }
    public static Set<String> obtenerMiembrosGrupo(String nombreGrupo) {
        Set<String> miembros = Collections.newSetFromMap(new ConcurrentHashMap<>());
        String sql = "SELECT usuario FROM GrupoMiembros WHERE nombre_grupo = ?";
        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, nombreGrupo);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                miembros.add(rs.getString("usuario"));
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error al obtener miembros: " + e.getMessage());
        }
        return miembros;
    }
    public static String obtenerHistorialGrupo(String nombreGrupo) {
        final int LIMITE_MENSAJES = 10;
        String sql = "SELECT mensaje FROM HistorialMensajes WHERE nombre_grupo = ? ORDER BY id DESC LIMIT ?";

        List<String> mensajes = new LinkedList<>();
        int totalMensajes = 0;

        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, nombreGrupo);
            pstmt.setInt(2, LIMITE_MENSAJES);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                mensajes.add(0, rs.getString("mensaje"));
                totalMensajes++;
            }
        } catch (SQLException e) {
            return "[ERROR GRUPO] Fallo al cargar historial: " + e.getMessage();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("--- %d MENSAJES RECIENTES EN '%s' ---\n", totalMensajes, nombreGrupo));

        if (mensajes.isEmpty()) {
            sb.append("[INFO GRUPO] No hay mensajes en este grupo.\n");
        } else {
            mensajes.forEach(msg -> sb.append(msg).append("\n"));
        }

        return sb.toString();
    }

    private static String obtenerYActualizarNoVistos(String usuario, Grupo grupo) {
        return obtenerHistorialGrupo(grupo.getNombre());
    }

    public static String actualizarVistosYMostrar(String nombreUsuario) {
        String nombreGrupo = obtenerNombreGrupoActual(nombreUsuario);
        Grupo grupo = grupos.get(nombreGrupo);
        if (grupo == null) return "[ERROR GRUPO] Grupo actual no encontrado.";

        return obtenerYActualizarNoVistos(nombreUsuario, grupo);
    }
}