package ServidorMulti;
import java.io.*;
import java.net.Socket;

public class UnCliente implements Runnable {

    final DataOutputStream salida;
    final DataInputStream entrada;
    public final String nombreCliente;

    UnCliente(Socket s, String nombre) throws IOException {
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
        this.nombreCliente = nombre;
    }

    @Override
    public void run() {
        String mensaje;
        while ( true ){
            try {
                mensaje = entrada.readUTF();
                String mensajeConRemitente = this.nombreCliente + ": " + mensaje;

                if (mensaje.startsWith("@")){
                    int indicePrimerEspacio = mensaje.indexOf(" ");

                    if (indicePrimerEspacio > 0) {
                        String listaDestinatariosConArroba = mensaje.substring(0, indicePrimerEspacio).trim();
                        String cuerpoMensaje = mensaje.substring(indicePrimerEspacio + 1).trim();
                        String mensajeAGrupo = "(PRIVADO de " + this.nombreCliente + ") " + cuerpoMensaje;

                        String listaDestinatarios = listaDestinatariosConArroba.substring(1);
                        String[] nombresDestinatarios = listaDestinatarios.split(",");
                        boolean enviado = false;

                        for (String nombre : nombresDestinatarios) {
                            String nombreLimpio = nombre.trim();
                            UnCliente cliente = ServidorMulti.clientes.get(nombreLimpio);

                            if (cliente != null) {
                                cliente.salida.writeUTF(mensajeAGrupo);
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
                            cliente.salida.writeUTF(mensajeConRemitente);
                        }
                    }

                }else {
                    for ( UnCliente cliente : ServidorMulti.clientes.values()){

                        if (cliente != this) {
                            cliente.salida.writeUTF(mensajeConRemitente);
                        }
                    }
                }
            } catch (IOException ex) {
                System.out.println(this.nombreCliente + " se ha desconectado.");
                ServidorMulti.clientes.remove(this.nombreCliente);
                break;
            }
        }
    }
}