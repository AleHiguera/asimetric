package Clientemulti;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ClienteMulti {

    public static void main(String[] args) throws IOException {
        BufferedReader tecladoInicial = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Introduce tu nombre de usuario: ");
        String nombreUsuario = tecladoInicial.readLine();

        Socket s = new Socket("localhost",8080);

        DataOutputStream salidaInicial = new DataOutputStream(s.getOutputStream());
        salidaInicial.writeUTF(nombreUsuario);

        ParaMandar paraMandar = new ParaMandar(s);
        Thread hiloParaMandar = new Thread(paraMandar);
        hiloParaMandar.start();

        ParaRecibir paraRecibir = new ParaRecibir(s);
        Thread hiloParaRecibir = new Thread(paraRecibir);
        hiloParaRecibir.start();
    }
}