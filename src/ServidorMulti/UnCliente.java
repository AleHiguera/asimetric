package ServidorMulti;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;

public class UnCliente implements Runnable {

    final DataOutputStream salida;
    final DataInputStream entrada;
    public final String nombreCliente;
    public final boolean esInvitado;
    private int mensajesGratisRestantes = 3;

    UnCliente(Socket s, String nombre, boolean esInvitado) throws IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.nombreCliente = nombre;
        this.esInvitado = esInvitado;
    }
    private void manejarComandos(String mensaje) throws IOException {
        String[] partes = mensaje.trim().split(" ", 2);
        String comando = partes[0].toLowerCase();
        boolean operacionExitosa = false;

        if (comando.equals("/bloquear") || comando.equals("/desbloquear")) {
            if (this.esInvitado) {
                this.salida.writeUTF("<<SERVIDOR>>: No puedes usar " + comando + " en MODO INVITADO. Inicia sesión o regístrate.");
                this.salida.flush();
                return;
            }
            if (partes.length != 2) {
                this.salida.writeUTF("<<SERVIDOR>>: Uso incorrecto. Debe ser: " + comando + " <nombre_usuario>");
                this.salida.flush();
                return;
            }

            String nombreObjetivo = partes[1].trim();

            if (nombreObjetivo.equalsIgnoreCase(this.nombreCliente)) {
                this.salida.writeUTF("<<SERVIDOR>>: No puedes bloquearte a ti mismo.");
                this.salida.flush();
                return;
            }
            if (!ServidorMulti.usuariosRegistrados.containsKey(nombreObjetivo)) {
                this.salida.writeUTF("<<SERVIDOR>>: Error, el usuario '" + nombreObjetivo + "' no existe para ser bloqueado/desbloqueado.");
                this.salida.flush();
                return;
            }

            if (comando.equals("/bloquear")) {
                if (ManejadorBloqueos.bloquearUsuario(this.nombreCliente, nombreObjetivo)) {
                    this.salida.writeUTF("<<SERVIDOR>>: Has bloqueado a '" + nombreObjetivo + "'. Ya no recibirás ni enviarás mensajes a este usuario.");
                    operacionExitosa = true;
                } else {
                    this.salida.writeUTF("<<SERVIDOR>>: El usuario '" + nombreObjetivo + "' ya estaba bloqueado.");
                }
            } else if (comando.equals("/desbloquear")) {
                if (ManejadorBloqueos.desbloquearUsuario(this.nombreCliente, nombreObjetivo)) {
                    this.salida.writeUTF("<<SERVIDOR>>: Has desbloqueado a '" + nombreObjetivo + "'.");
                    operacionExitosa = true;
                } else {
                    this.salida.writeUTF("<<SERVIDOR>>: El usuario '" + nombreObjetivo + "' no estaba bloqueado.");
                }
            }
            if (operacionExitosa) {
                this.salida.writeUTF("<<SERVIDOR>>: Usa '@u1 Mensaje' o '@u1,u2 Mensaje' para enviar privados.");
                this.salida.writeUTF("<<SERVIDOR>>: Usa '/bloquear <usuario>' y '/desbloquear <usuario>' para gestionar bloqueos.");
            }

            this.salida.flush();
            return;
        }
    }

    private boolean manejarMensajeInvitado(String mensaje) throws IOException {
        if (!esInvitado) return false;

        String comando = mensaje.trim().toLowerCase();
        if (comando.equals("/iniciar") || comando.equals("/registro") || comando.startsWith("/bloquear") || comando.startsWith("/desbloquear")) {
            return true;
        }

        if (mensajesGratisRestantes <= 0) {
            this.salida.writeUTF("<<SERVIDOR>>: Agotaste tus 3 mensajes gratis. Escribe '/iniciar' o '/registro' para continuar.");
            this.salida.flush();
            return true;
        }
        mensajesGratisRestantes--;
        String advertencia;

        if (mensajesGratisRestantes > 0) {
            advertencia = "<<SERVIDOR>>: Te quedan:" + mensajesGratisRestantes + " mensajes gratis.";
        } else {
            advertencia = "<<SERVIDOR>>: Este fue tu último mensaje gratis. Escribe '/iniciar' o '/registro' para continuar chateando.";
        }
        this.salida.writeUTF(advertencia);
        this.salida.flush();
        return false;
    }

    private void manejarMensajePrivado(String mensaje) throws IOException {
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
                String remitente = this.nombreCliente;

                if (cliente != null) {
                    if (ManejadorBloqueos.esUsuarioBloqueado(remitente, nombreLimpio)) {
                        this.salida.writeUTF("<<SERVIDOR>>: Tu mensaje a '" + nombreLimpio + "' no fue entregado. Has sido bloqueado por ese usuario.");
                        enviado = true;
                        continue;
                    }
                    if (ManejadorBloqueos.estaBloqueadoPor(remitente, nombreLimpio)) {
                        this.salida.writeUTF("<<SERVIDOR>>: Tu mensaje a '" + nombreLimpio + "' no fue entregado. Lo tienes bloqueado.");
                        enviado = true;
                        continue;
                    }


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
    }

    private void transmitirMensajePublico(String mensajeConRemitente) throws IOException {
        String remitente = this.nombreCliente;

        for (UnCliente cliente : ServidorMulti.clientes.values()){
            if (cliente != this) {
                if (ManejadorBloqueos.esUsuarioBloqueado(remitente, cliente.nombreCliente)) {
                    continue;
                }
                if (ManejadorBloqueos.estaBloqueadoPor(remitente, cliente.nombreCliente)) {
                    continue;
                }

                cliente.salida.writeUTF(mensajeConRemitente);
                cliente.salida.flush();
            }
        }
    }

    private void manejarDesconexion() {
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
    }

    @Override
    public void run() {
        String mensaje;
        try {
            while (true) {
                mensaje = entrada.readUTF();
                if (mensaje.startsWith("/")) {
                    manejarComandos(mensaje);
                    continue;
                }

                String mensajeConRemitente = this.nombreCliente + ": " + mensaje;

                if (manejarMensajeInvitado(mensaje)) {
                    continue;
                }

                if (mensaje.startsWith("@")) {
                    manejarMensajePrivado(mensaje);
                } else {
                    transmitirMensajePublico(mensajeConRemitente);
                }
            }
        } catch (IOException ex) {
            manejarDesconexion();

        }
    }
}