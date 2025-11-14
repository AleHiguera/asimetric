package ServidorMulti;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteManager {
    private static final String URL = "jdbc:sqlite:server_data.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
    public static void inicializarDB() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Tabla de Usuarios
            stmt.execute("CREATE TABLE IF NOT EXISTS Usuarios (" +
                    "usuario TEXT PRIMARY KEY NOT NULL," +
                    "password TEXT NOT NULL)");

            //  Tabla de Bloqueos
            stmt.execute("CREATE TABLE IF NOT EXISTS Bloqueos (" +
                    "bloqueador TEXT NOT NULL," +
                    "bloqueado TEXT NOT NULL," +
                    "PRIMARY KEY (bloqueador, bloqueado))");

            // Tabla de Rankings
            stmt.execute("CREATE TABLE IF NOT EXISTS Rankings (" +
                    "usuario TEXT PRIMARY KEY NOT NULL," +
                    "puntos INTEGER DEFAULT 0," +
                    "victorias INTEGER DEFAULT 0," +
                    "derrotas INTEGER DEFAULT 0," +
                    "empates INTEGER DEFAULT 0)");

            // Tabla Grupos
            stmt.execute("CREATE TABLE IF NOT EXISTS Grupos (" +
                    "nombre TEXT PRIMARY KEY NOT NULL," +
                    "creador TEXT NOT NULL)");

            // Tabla GrupoMiembros
            stmt.execute("CREATE TABLE IF NOT EXISTS GrupoMiembros (" +
                    "nombre_grupo TEXT NOT NULL," +
                    "usuario TEXT NOT NULL," +
                    "PRIMARY KEY (nombre_grupo, usuario)," +
                    "FOREIGN KEY (nombre_grupo) REFERENCES Grupos(nombre))");

            // Tabla HistorialMensajes
            stmt.execute("CREATE TABLE IF NOT EXISTS HistorialMensajes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "nombre_grupo TEXT NOT NULL," +
                    "mensaje TEXT NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (nombre_grupo) REFERENCES Grupos(nombre))");

            System.out.println("Base de datos SQLite inicializada correctamente.");

        } catch (SQLException e) {
            System.err.println("Error al inicializar la base de datos: " + e.getMessage());
        }
    }
}