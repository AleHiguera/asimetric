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
    public JuegoGato juegoActual = null;

    UnCliente(Socket s, String nombre, boolean esInvitado) throws IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.nombreCliente = nombre;
        this.esInvitado = esInvitado;
    }
    private void enviarServidor(String msg) throws IOException {
        this.salida.writeUTF("<<SERVIDOR>>: " + msg);
        this.salida.flush();
    }

    private UnCliente getOponente(JuegoGato juego) {
        if (juego == null) return null;
        if (juego.getJugadorX() == this) return juego.getJugadorO();
        if (juego.getJugadorO() == this) return juego.getJugadorX();
        return null;
    }

    private void manejarComandos(String mensaje) throws IOException {
        String[] partes = mensaje.trim().split(" ", 2);
        String comando = partes[0].toLowerCase();
        boolean operacionExitosa = false;

        if (comando.equals("/gato")) {
            if (this.esInvitado) {
                enviarServidor("No puedes iniciar el 'No Viudo' en MODO INVITADO.");
                return;
            }
            if (this.juegoActual != null) {
                enviarServidor("Ya estás jugando una partida de 'No Viudo' con " + getOponente(this.juegoActual).nombreCliente + ".");
                return;
            }
            if (partes.length != 2) {
                enviarServidor("Uso incorrecto. Debe ser: /gato <nombre_usuario>");
                return;
            }
            String nombreObjetivo = partes[1].trim();
            if (nombreObjetivo.equalsIgnoreCase(this.nombreCliente)) {
                enviarServidor("No puedes invitarte a ti mismo. ¡Ya no serías 'No Viudo'!");
                return;
            }
            UnCliente objetivo = ServidorMulti.clientes.get(nombreObjetivo);
            if (objetivo == null) {
                enviarServidor("El usuario '" + nombreObjetivo + "' no está conectado.");
                return;
            }
            if (objetivo.juegoActual != null) {
                enviarServidor("El usuario '" + nombreObjetivo + "' ya está ocupado jugando.");
                return;
            }
            if (ManejadorBloqueos.esUsuarioBloqueado(this.nombreCliente, nombreObjetivo) ||
                    ManejadorBloqueos.estaBloqueadoPor(this.nombreCliente, nombreObjetivo)) {
                enviarServidor("No puedes invitar a '" + nombreObjetivo + "' debido a un bloqueo mutuo o unidireccional.");
                return;
            }
            if (ServidorMulti.propuestasPendientes.containsKey(nombreObjetivo)) {
                enviarServidor("El usuario '" + nombreObjetivo + "' ya tiene una propuesta pendiente. Espera a que responda.");
                return;
            }
            ServidorMulti.propuestasPendientes.put(nombreObjetivo, this.nombreCliente);
            enviarServidor("Propuesta enviada a '" + nombreObjetivo + "'. Esperando respuesta...");
            objetivo.salida.writeUTF("<<NO VIUDO>>: ¡" + this.nombreCliente + " te desafía a una partida de Gato 'No Viudo'!");
            objetivo.salida.writeUTF("<<NO VIUDO>>: Responde: /aceptar o /rechazar");
            objetivo.salida.flush();
            return;
        }

        if (comando.equals("/aceptar") || comando.equals("/rechazar")) {
            String proponente = ServidorMulti.propuestasPendientes.remove(this.nombreCliente);

            if (proponente == null) {
                enviarServidor("No tienes ninguna propuesta de juego pendiente para responder.");
                return;
            }

            UnCliente clienteProponente = ServidorMulti.clientes.get(proponente);
            if (clienteProponente == null) {
                enviarServidor("El proponente se ha desconectado. Intenta de nuevo.");
                return;
            }

            if (comando.equals("/aceptar")) {
                if (clienteProponente.juegoActual != null) {
                    enviarServidor("El proponente ya comenzó otra partida. Rechazo automático.");
                    clienteProponente.salida.writeUTF("<<NO VIUDO>>: Tu propuesta fue rechazada automáticamente porque iniciaste otro juego.");
                    return;
                }

                JuegoGato nuevoJuego = new JuegoGato(this, clienteProponente);
                ServidorMulti.juegosActivos.put(nuevoJuego.getSessionId(), nuevoJuego);
                this.juegoActual = nuevoJuego;
                clienteProponente.juegoActual = nuevoJuego;
                nuevoJuego.iniciarJuego();

                clienteProponente.salida.writeUTF("<<NO VIUDO>>: ¡" + this.nombreCliente + " ha aceptado el desafío!");
                clienteProponente.salida.flush();

            } else if (comando.equals("/rechazar")) {
                enviarServidor("Has rechazado la propuesta de " + proponente + ".");
                clienteProponente.salida.writeUTF("<<NO VIUDO>>: " + this.nombreCliente + " ha rechazado tu desafío.");
                clienteProponente.salida.flush();
            }
            return;
        }
        if (comando.equals("/si") || comando.equals("/no")) {
            String oponenteNombre = ServidorMulti.propuestasRejuego.remove(this.nombreCliente);

            if (oponenteNombre == null) {
                if (comando.equals("/si")) {
                    enviarServidor("No tienes una partida para reiniciar. Usa /gato <usuario> para iniciar una nueva.");
                } else {
                    enviarServidor("Volviendo a actividades normales de chat.");
                }
                return;
            }
            ServidorMulti.propuestasRejuego.remove(oponenteNombre);

            UnCliente oponente = ServidorMulti.clientes.get(oponenteNombre);

            if (oponente == null || oponente.juegoActual != null) {
                enviarServidor("Tu oponente se ha desconectado o está ocupado. Volviendo a chat normal.");
                return;
            }

            if (comando.equals("/si")) {
                JuegoGato nuevoJuego = new JuegoGato(this, oponente);
                ServidorMulti.juegosActivos.put(nuevoJuego.getSessionId(), nuevoJuego);
                this.juegoActual = nuevoJuego;
                oponente.juegoActual = nuevoJuego;
                nuevoJuego.iniciarJuego();
                oponente.salida.writeUTF("<<NO VIUDO>>: ¡" + this.nombreCliente + " ha aceptado jugar de nuevo!");
                oponente.salida.flush();

            } else if (comando.equals("/no")) {
                enviarServidor("Has rechazado el re-juego. Volviendo a chat normal.");
                oponente.salida.writeUTF("<<NO VIUDO>>: " + this.nombreCliente + " ha rechazado jugar de nuevo. Volviendo a chat normal.");
                oponente.salida.flush();
            }
            return;
        }
        if (comando.equals("/jugar")) {
            enviarServidor("Usa /gato <usuario> para invitar a alguien a una partida de 'No Viudo'.");
            enviarServidor("Usuarios conectados: " + ServidorMulti.clientes.keySet());
            return;
        }
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
        if (comando.equals("/iniciar") || comando.equals("/registro") || comando.startsWith("/bloquear") || comando.startsWith("/desbloquear") || comando.startsWith("/gato") || comando.startsWith("/aceptar") || comando.startsWith("/rechazar") || comando.startsWith("/si") || comando.startsWith("/no") || comando.startsWith("/jugar")) {
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
                    if (cliente.juegoActual != null) {
                        this.salida.writeUTF("<<SERVIDOR>>: Tu mensaje a '" + nombreLimpio + "' no fue entregado. Está ocupado jugando una partida 'No Viudo'.");
                        enviado = true;
                        continue;
                    }

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
                if (cliente.juegoActual != null) {
                    continue;
                }

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
        if (this.juegoActual != null) {
            try {
                this.juegoActual.forzarVictoria(this);
                ServidorMulti.juegosActivos.remove(this.juegoActual.getSessionId());
            } catch (IOException e) {
                System.err.println("Error al notificar victoria por desconexión.");
            }
        }

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
                if (this.juegoActual != null && !mensaje.startsWith("/")) {
                    try {
                        String[] coords = mensaje.trim().split(",");
                        int fila = Integer.parseInt(coords[0].trim());
                        int col = Integer.parseInt(coords[1].trim());

                        this.juegoActual.mover(this, fila, col);

                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                        enviarServidor("Formato de jugada incorrecto. Usa 'Fila,Columna' (ej: 1,2).");
                    }
                    continue;
                }
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