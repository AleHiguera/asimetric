package Clientemulti;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class ParaRecibir implements Runnable{
    final DataInputStream entrada;
    private final Socket socketCliente;

    public ParaRecibir(Socket s) throws IOException {
        entrada = new DataInputStream(s.getInputStream());
        this.socketCliente = s;
    }

    @Override
    public void run() {
        String mensaje;

        while(true){
            try {
                mensaje = entrada.readUTF();
                System.out.print("\r" + mensaje + "\n> ");

            } catch (IOException ex) {
                System.out.println("\n[DESCONEXIÓN] Conexión con el servidor perdida o finalizada. Volviendo al menú...");
                try {
                    if (socketCliente != null && !socketCliente.isClosed()) {
                        socketCliente.close();
                    }
                } catch (IOException ignored) {

                }
                break;
            }
        }
    }
}