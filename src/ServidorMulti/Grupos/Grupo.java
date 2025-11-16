package ServidorMulti.Grupos;
public class Grupo {
    private final String nombre;
    private final String creador;

    public Grupo(String nombre, String creador) {
        this.nombre = nombre;
        this.creador = creador;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCreador() {
        return creador;
    }
    public boolean esCreador(String nombreUsuario) {
        return nombreUsuario.equals(creador);
    }

}