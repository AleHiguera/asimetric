package ServidorMulti;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BlockListManager {

    private BlockListManager() {}
    public static boolean bloquearUsuario(String bloqueador, String bloqueado) {
        String sql = "INSERT INTO Bloqueos (bloqueador, bloqueado) VALUES (?, ?)";
        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, bloqueador);
            pstmt.setString(2, bloqueado);
            pstmt.executeUpdate();
            return true;

        } catch (SQLException e) {
            if (e.getErrorCode() == 19) {
                return false;
            }
            System.err.println("[DB] Error al bloquear usuario: " + e.getMessage());
            return false;
        }
    }
    public static boolean desbloquearUsuario(String bloqueador, String bloqueado) {
        String sql = "DELETE FROM Bloqueos WHERE bloqueador = ? AND bloqueado = ?";
        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, bloqueador);
            pstmt.setString(2, bloqueado);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("[DB] Error al desbloquear usuario: " + e.getMessage());
            return false;
        }
    }
    public static boolean estaBloqueado(String receptor, String remitente) {
        String sql = "SELECT 1 FROM Bloqueos WHERE bloqueador = ? AND bloqueado = ?";
        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, receptor);
            pstmt.setString(2, remitente);

            ResultSet rs = pstmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            System.err.println("[DB] Error al consultar bloqueo: " + e.getMessage());
            return false;
        }
    }
}