package ServidorMulti;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ClienteAuthManager {
    private ClienteAuthManager() {}
    public static boolean registrarUsuario(String usuario, String password) {
        // Usa INSERT para añadir un nuevo registro. El constraint PRIMARY KEY impide duplicados.
        String sql = "INSERT INTO Usuarios (usuario, password) VALUES (?, ?)";
        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usuario);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            return true;

        } catch (SQLException e) {
            if (e.getErrorCode() == 19) {
                System.out.println("[DB] Intento de registro de usuario existente: " + usuario);
            } else {
                System.err.println("[DB] Error al registrar usuario: " + e.getMessage());
            }
            return false;
        }
    }
    public static boolean verificarCredenciales(String usuario, String password) {
        String sql = "SELECT password FROM Usuarios WHERE usuario = ?";
        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usuario);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return password.equals(rs.getString("password"));
            }
            return false;
        } catch (SQLException e) {
            System.err.println("[DB] Error al verificar credenciales: " + e.getMessage());
            return false;
        }
    }
    public static boolean existeUsuario(String usuario) {
        String sql = "SELECT 1 FROM Usuarios WHERE usuario = ?";
        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usuario);
            ResultSet rs = pstmt.executeQuery();

            return rs.next(); // Si hay un resultado, el usuario existe.
        } catch (SQLException e) {
            System.err.println("[DB] Error al verificar existencia de usuario: " + e.getMessage());
            return false;
        }
    }
}