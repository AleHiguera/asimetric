package ServidorMulti;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ServidorMulti {
    static HashMap<String, UnCliente> clientes = new HashMap<String, UnCliente>();
    static Map<String, String> usuariosRegistrados;

    public static void main(String[] args) throws IOException {
        usuariosRegistrados = ManejadorUsuarios.cargarUsuarios();

        ServerSocket servidorSocket = new ServerSocket(8080);
        System.out.println("Servidor iniciado en puerto 8080. Esperando clientes...");

        int contadorInvitados = 0;

        while (true) {
            Socket s = servidorSocket.accept();
            String nombreFinal = null;

            try {
                DataInputStream entradaInicial = new DataInputStream(s.getInputStream());
                String datosConexion = entradaInicial.readUTF();
                String[] partes = datosConexion.split(":", 3);
                String accion = partes[0];
                DataOutputStream salidaInicial = new DataOutputStream(s.getOutputStream());
                if (accion.equals("INVITADO")) {
                    contadorInvitados++;
                    nombreFinal = "Invitado" + contadorInvitados;
                    salidaInicial.writeUTF("OK_INVITADO:" + nombreFinal);

                } else if (accion.equals("REGISTRO")) {
                    String usuario = partes[1];
                    String contrasena = partes[2];

                    if (ManejadorUsuarios.registrarUsuario(usuario, contrasena)) {
                        nombreFinal = usuario;
                        salidaInicial.writeUTF("OK_REGISTRO:" + nombreFinal);
                    } else {
                        salidaInicial.writeUTF("ERROR_REGISTRO:El nombre de usuario '" + usuario + "' ya existe.");
                        s.close();
                        continue;
                    }
                } else if (accion.equals("INICIO_SESION")) {
                    String usuario = partes[1];
                    String contrasena = partes[2];

                    if (ManejadorUsuarios.verificarCredenciales(usuario, contrasena)) {
                        if (clientes.containsKey(usuario)) {
                            salidaInicial.writeUTF("ERROR_LOGIN:El usuario '" + usuario + "' ya está conectado.");
                            s.close();
                            continue;
                        }
                        nombreFinal = usuario;
                        salidaInicial.writeUTF("OK_LOGIN:" + nombreFinal);
                    } else {
                        salidaInicial.writeUTF("ERROR_LOGIN:Credenciales incorrectas o usuario no existe.");
                        s.close();
                        continue;
                    }
                } else {
                    salidaInicial.writeUTF("ERROR_CONEXION:Acción desconocida.");
                    s.close();
                    continue;
                }
                if (clientes.containsKey(nombreFinal)) {
                    System.err.println("Error interno: Nombre duplicado detectado después de OK_.");
                    s.close();
                    continue;
                }

                UnCliente unCliente = new UnCliente(s, nombreFinal, accion.equals("INVITADO"));
                Thread hilo = new Thread(unCliente);
                clientes.put(nombreFinal, unCliente);
                hilo.start();

                System.out.println("Se conectó el chango: " + nombreFinal);
                unCliente.salida.writeUTF("<<SERVIDOR>>: Bienvenido/a al chat, " + nombreFinal + "!");
                unCliente.salida.writeUTF("<<SERVIDOR>>: Usa '@u1 Mensaje' o '@u1,u2 Mensaje' para enviar privados.");

                if (unCliente.esInvitado) {
                    unCliente.salida.writeUTF("<<SERVIDOR>>: Estás en MODO INVITADO. Tienes 3 mensajes gratis.");
                }
                unCliente.salida.flush();

            } catch (IOException e) {
                System.err.println("Error al manejar la conexión inicial: " + e.getMessage());
                s.close();
            }
        }
    }
}