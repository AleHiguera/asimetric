package Clientemulti;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ParaMandar implements Runnable{
    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    final DataOutputStream salida ;
    private final Socket socketCliente;

    public ParaMandar(Socket s) throws IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.socketCliente = s;
    }

    @Override
    public void run() {
        while ( true ){
            String mensaje;
            try {
                mensaje = teclado.readLine();
                String comando = mensaje.trim().toLowerCase();
                if (comando.equals("/iniciar") || comando.equals("/registro")) {
                    System.out.println("\n[COMANDO DETECTADO] Cerrando conexión para volver al menú de autenticación...");

                    if (!socketCliente.isClosed()) {
                        socketCliente.close();
                    }
                    break;
                }

                salida.writeUTF(mensaje);
            } catch (IOException ex) {
                break;
            }
        }
    }
}
