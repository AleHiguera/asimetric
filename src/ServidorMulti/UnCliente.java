package ServidorMulti;
import java.io.*;
import java.net.Socket;

public class UnCliente implements Runnable {

    final DataOutputStream salida;

    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

    final DataInputStream entrada;

    UnCliente(Socket s) throws IOException {
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        String mensaje;
        while ( true ){
            try {
                mensaje = entrada.readUTF();
                if (mensaje.startsWith("@")){
                    int indicePrimerEspacio = mensaje.indexOf(" ");
                    if (indicePrimerEspacio > 0) {
                        String listaDestinatariosConArroba = mensaje.substring(0, indicePrimerEspacio).trim();
                        String cuerpoMensaje = mensaje.substring(indicePrimerEspacio + 1).trim();
                        String listaDestinatarios = listaDestinatariosConArroba.substring(1);
                        String[] nombresDestinatarios = listaDestinatarios.split(",");
                        boolean enviado = false;
                        for (String nombre : nombresDestinatarios) {
                            String nombreLimpio = nombre.trim();
                            UnCliente cliente = ServidorMulti.clientes.get(nombreLimpio);

                            if (cliente != null) {
                                cliente.salida.writeUTF(mensaje);
                                enviado = true;
                            }
                        }
                        if (!enviado) {
                            System.err.println("Advertencia: No se encontró a ningún destinatario para: " + listaDestinatarios);
                        }

                    } else {
                        String aQuien = mensaje.substring(1).trim();
                        UnCliente cliente = ServidorMulti.clientes.get(aQuien);

                        if (cliente != null) {
                            cliente.salida.writeUTF(mensaje);
                        }
                    }

                }else {
                    for ( UnCliente cliente : ServidorMulti.clientes.values()){
                        cliente.salida.writeUTF(mensaje);
                    }  }
            } catch (IOException ex) {
            }
        }
    }
}