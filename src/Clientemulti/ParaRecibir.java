package Clientemulti;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class ParaRecibir implements Runnable{
    final DataInputStream entrada;

    public ParaRecibir(Socket s) throws IOException {
        entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        String mensaje;

        while(true){
            try {
                mensaje = entrada.readUTF();
                System.out.print("\r" + mensaje + "\n> ");

            } catch (IOException ex) {
                System.out.println("\n[ERROR] Conexión con el servidor perdida. Terminando...");
                break;
            }
        }
    }
}