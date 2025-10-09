package ServidorMulti;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class ServidorMulti {
    static HashMap<String, UnCliente> clientes = new HashMap<String, UnCliente>();

    public static void main(String[] args) throws IOException {
        ServerSocket servidorSocket = new ServerSocket(8080);

        System.out.println("Servidor iniciado en puerto 8080. Esperando clientes...");

        while (true) {
            Socket s = servidorSocket.accept();

            try {
                DataInputStream entradaInicial = new DataInputStream(s.getInputStream());
                String nombre = entradaInicial.readUTF();

                if (clientes.containsKey(nombre)) {
                    System.out.println("El nombre '" + nombre + "' ya está en uso. Conexión rechazada.");
                    s.close();
                    continue;
                }
                UnCliente unCliente = new UnCliente(s, nombre);
                Thread hilo = new Thread(unCliente);
                clientes.put(nombre, unCliente);
                hilo.start();

                System.out.println("Se conectó el chango: " + nombre);

            } catch (IOException e) {
                System.err.println("Error al manejar la conexión inicial: " + e.getMessage());
                s.close();
            }
        }
    }
}