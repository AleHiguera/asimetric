package ServidorMulti;

import java.io.*;
import java.net.Socket;

public class UnCliente implements Runnable {

    final DataOutputStream salida;
    final DataInputStream entrada;
    public final String nombreCliente;
    public final boolean esInvitado;
    private int mensajesGratisRestantes = 3;

    UnCliente(Socket s, String nombre, boolean esInvitado) throws IOException {
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
        this.nombreCliente = nombre;
        this.esInvitado = esInvitado;
    }

    @Override
    public void run() {
        String mensaje;
        while ( true ){
            try {
                mensaje = entrada.readUTF();
                String mensajeConRemitente = this.nombreCliente + ": " + mensaje;
                if (esInvitado) {
                    String comando = mensaje.trim().toLowerCase();
                    if (comando.equals("/iniciar") || comando.equals("/registro")) {
                        continue;
                    }

                    if (mensajesGratisRestantes <= 0) {
                        this.salida.writeUTF("<<SERVIDOR>>: Agotaste tus 3 mensajes gratis. Escribe '/iniciar' o '/registro' para continuar.");
                        this.salida.flush();
                        continue;
                    }

                    mensajesGratisRestantes--;

                    if (mensajesGratisRestantes > 0) {
                        this.salida.writeUTF("<<SERVIDOR>>: Te quedan:" + mensajesGratisRestantes + " mensajes gratis.");
                    } else {
                        this.salida.writeUTF("<<SERVIDOR>>: Este fue tu último mensaje gratis. Escribe '/iniciar' o '/registro' para continuar chateando.");
                    }
                }

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
                                cliente.salida.flush();
                                enviado = true;
                            }
                        }

                        if (!enviado) {
                            System.err.println("Advertencia: No se encontró a ningún destinatario para: " + listaDestinatarios);
                            this.salida.writeUTF("<<SERVIDOR>>: Error, no se encontró a los destinatarios: " + listaDestinatarios);
                            this.salida.flush();
                        }

                    } else {
                        this.salida.writeUTF("<<SERVIDOR>>: Formato de mensaje privado incorrecto. Debe ser: @nombre Mensaje.");
                        this.salida.flush();
                    }

                }else {
                    for ( UnCliente cliente : ServidorMulti.clientes.values()){

                        if (cliente != this) {
                            cliente.salida.writeUTF(mensajeConRemitente);
                            cliente.salida.flush();
                        }
                    }
                }
            } catch (IOException ex) {
                System.out.println(this.nombreCliente + " se ha desconectado.");
                ServidorMulti.clientes.remove(this.nombreCliente);
                String mensajeDesconexion = "<<SERVIDOR>>: " + this.nombreCliente + " se ha desconectado.";
                for (UnCliente cliente : ServidorMulti.clientes.values()) {
                    try {
                        cliente.salida.writeUTF(mensajeDesconexion);
                        cliente.salida.flush();
                    } catch (IOException e) {
                    }
                }
                break;
            }
        }
    }
}
