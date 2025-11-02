package ServidorMulti;import java.io.*;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class UnCliente implements Runnable {

    final DataOutputStream salida;
    final DataInputStream entrada;
    public final String nombreCliente;
    public final boolean esInvitado;
    private int mensajesGratisRestantes = 3;
    public final Map<String, JuegoGato> juegosActivos = new HashMap<>();

    UnCliente(Socket s, String nombre, boolean esInvitado) throws IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.nombreCliente = nombre;
        this.esInvitado = esInvitado; }
    private void enviarServidor(String msg) throws IOException {
        this.salida.writeUTF("<<SERVIDOR>>: " + msg);
        this.salida.flush(); }

    private UnCliente getOponente(JuegoGato juego) {
        if (juego == null) return null;
        if (juego.getJugadorX() == this) return juego.getJugadorO();
        if (juego.getJugadorO() == this) return juego.getJugadorX();
        return null;}

    private void manejarComandos(String mensaje) throws IOException {
        String[] partes = mensaje.trim().split(" ", 2);
        String comando = partes[0].toLowerCase();
        boolean operacionExitosa = false;

        if (comando.equals("/gato")) {
            if (this.esInvitado) { enviarServidor("No puedes iniciar el 'No Viudo' en MODO INVITADO.");
                return; }

            if (partes.length != 2) { enviarServidor("Uso incorrecto. Debe ser: /gato <nombre_usuario>");
                return; }

            String nombreObjetivo = partes[1].trim();

            if (nombreObjetivo.equalsIgnoreCase(this.nombreCliente)) {
                enviarServidor("No puedes invitarte a ti mismo. ¡Ya no serías 'No Viudo'!");
                return; }

            if (this.juegosActivos.containsKey(nombreObjetivo)) {
                enviarServidor("Ya estás jugando una partida de 'No Viudo' con " + nombreObjetivo + ".");
                return;
            }

            UnCliente objetivo = ServidorMulti.clientes.get(nombreObjetivo);
            if (objetivo == null) {
                enviarServidor("El usuario '" + nombreObjetivo + "' no está conectado.");
                return; }

            if (objetivo.juegosActivos.containsKey(this.nombreCliente)) {
                enviarServidor("El usuario '" + nombreObjetivo + "' ya está ocupado jugando contigo.");
                return;
            }

            if (ManejadorBloqueos.esUsuarioBloqueado(this.nombreCliente, nombreObjetivo) ||
                    ManejadorBloqueos.estaBloqueadoPor(this.nombreCliente, nombreObjetivo)) {
                enviarServidor("No puedes invitar a '" + nombreObjetivo + "' debido a un bloqueo mutuo o unidireccional.");
                return; }
            if (ServidorMulti.propuestasPendientes.containsKey(nombreObjetivo)) {
                enviarServidor("El usuario '" + nombreObjetivo + "' ya tiene una propuesta pendiente. Espera a que responda.");
                return; }
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
                return; }

            UnCliente clienteProponente = ServidorMulti.clientes.get(proponente);
            if (clienteProponente == null) {
                enviarServidor("El proponente se ha desconectado. Intenta de nuevo.");
                return; }

            if (comando.equals("/aceptar")) {
                if (clienteProponente.juegosActivos.containsKey(this.nombreCliente)) {
                    enviarServidor("El proponente ya comenzó otra partida contigo por otro medio. Rechazo automático.");
                    clienteProponente.salida.writeUTF("<<NO VIUDO>>: Tu propuesta fue rechazada automáticamente porque ya tienes un juego con " + this.nombreCliente + ".");
                    return;
                }

                JuegoGato nuevoJuego = new JuegoGato(this, clienteProponente);

                this.juegosActivos.put(proponente, nuevoJuego);
                clienteProponente.juegosActivos.put(this.nombreCliente, nuevoJuego);

                nuevoJuego.iniciarJuego();

                clienteProponente.salida.writeUTF("<<NO VIUDO>>: ¡" + this.nombreCliente + " ha aceptado el desafío!");
                clienteProponente.salida.flush();

            } else if (comando.equals("/rechazar")) {
                enviarServidor("Has rechazado la propuesta de " + proponente + ".");
                clienteProponente.salida.writeUTF("<<NO VIUDO>>: " + this.nombreCliente + " ha rechazado tu desafío.");
                clienteProponente.salida.flush(); }
            return;
        }
        if (comando.equals("/si") || comando.equals("/no")) {
            String oponenteNombre = ServidorMulti.propuestasRejuego.get(this.nombreCliente);

            if (oponenteNombre == null) {
                enviarServidor("No tienes una propuesta de re-juego pendiente.");
                return; }
            if (oponenteNombre.endsWith("/si") || oponenteNombre.endsWith("/no")) {
                enviarServidor("Ya respondiste a la propuesta de re-juego. Espera la respuesta de tu oponente.");
                return; }

            UnCliente oponente = ServidorMulti.clientes.get(oponenteNombre);

            if (oponente == null) {
                ServidorMulti.propuestasRejuego.remove(this.nombreCliente);
                enviarServidor("Tu oponente se ha desconectado. Volviendo a chat normal.");
                return; }
            String estadoOponente = ServidorMulti.propuestasRejuego.get(oponenteNombre);

            if (estadoOponente != null && (estadoOponente.endsWith("/si") || estadoOponente.endsWith("/no"))) {
                if (comando.equals("/si") && estadoOponente.endsWith("/si")) {
                    ServidorMulti.propuestasRejuego.remove(this.nombreCliente);
                    ServidorMulti.propuestasRejuego.remove(oponenteNombre);

                    if (this.juegosActivos.containsKey(oponenteNombre) || oponente.juegosActivos.containsKey(this.nombreCliente)) {
                        enviarServidor("Uno de los jugadores ya inició otro juego contigo. Re-juego CANCELADO.");
                        oponente.salida.writeUTF("<<NO VIUDO>>: El re-juego fue cancelado, uno de los jugadores inició otro juego contigo.");
                        return;
                    }

                    JuegoGato nuevoJuego = new JuegoGato(this, oponente);
                    this.juegosActivos.put(oponenteNombre, nuevoJuego);
                    oponente.juegosActivos.put(this.nombreCliente, nuevoJuego);

                    nuevoJuego.iniciarJuego();

                    oponente.salida.writeUTF("<<NO VIUDO>>: ¡" + this.nombreCliente + " ha aceptado! Iniciando nuevo juego.");
                    enviarServidor("¡Has aceptado! Iniciando nuevo juego con " + oponenteNombre + ".");

                } else {
                    ServidorMulti.propuestasRejuego.remove(this.nombreCliente);
                    ServidorMulti.propuestasRejuego.remove(oponenteNombre);

                    enviarServidor("El re-juego ha sido rechazado. Volviendo a chat normal.");
                    oponente.salida.writeUTF("<<NO VIUDO>>: " + this.nombreCliente + " ha respondido. Re-juego CANCELADO. Volviendo a chat normal.");
                }
                return;
            }
            if (comando.equals("/si")) {
                ServidorMulti.propuestasRejuego.put(this.nombreCliente, oponenteNombre + "/si");
                enviarServidor("Has aceptado el re-juego. Esperando la respuesta de " + oponenteNombre + "...");
                oponente.salida.writeUTF("<<NO VIUDO>>: " + this.nombreCliente + " ACEPTÓ el re-juego. Responde /si o /no para iniciar.");
            } else {
                ServidorMulti.propuestasRejuego.remove(this.nombreCliente);
                ServidorMulti.propuestasRejuego.remove(oponenteNombre);

                enviarServidor("Has rechazado el re-juego. Volviendo a chat normal.");
                oponente.salida.writeUTF("<<NO VIUDO>>: " + this.nombreCliente + " ha RECHAZADO jugar de nuevo. Volviendo a chat normal.");
            }
            return;
        }
        if (comando.equals("/vs")) {
            if (this.esInvitado) {
                enviarServidor("No puedes usar /vs en MODO INVITADO.");
                return;
            }

            if (partes.length != 2) {
                enviarServidor("Uso incorrecto. Debe ser: /vs <usuario1> <usuario2>");
                return;
            }
            String[] usuarios = partes[1].trim().split(" ", 2);
            if (usuarios.length != 2) {
                enviarServidor("Uso incorrecto. Debes proporcionar DOS nombres de usuario: /vs <usuario1> <usuario2>");
                return;
            }

            String usuario1 = usuarios[0].trim();
            String usuario2 = usuarios[1].trim();

            if (usuario1.equalsIgnoreCase(usuario2)) {
                enviarServidor("No puedes compararte contigo mismo. Usa /puntos.");
                return;
            }

            String vsStr = ManejadorRanking.getEstadisticasVs(usuario1, usuario2);
            enviarServidor(vsStr);
            return;
        }
        if (comando.equals("/ranking")) {
            if (this.esInvitado) {
                enviarServidor("No puedes ver el ranking en MODO INVITADO.");
                return;
            }
            String rankingStr = ManejadorRanking.getRankingGeneral();
            enviarServidor(rankingStr);
            return;
        }

        if (comando.equals("/puntos")) {
            if (this.esInvitado) {
                enviarServidor("No puedes ver estadísticas en MODO INVITADO.");
                return;
            }
            String nombreObjetivo;
            if (partes.length == 1) {
                nombreObjetivo = this.nombreCliente;
            } else {
                nombreObjetivo = partes[1].trim();
            }

            if (!ServidorMulti.usuariosRegistrados.containsKey(nombreObjetivo)) {
                enviarServidor("El usuario '" + nombreObjetivo + "' no existe.");
                return;
            }

            EstadisticasJugador stats = ManejadorRanking.getEstadisticas(nombreObjetivo);
            int totalPartidas = stats.getTotalPartidas();

            if (totalPartidas == 0) {
                enviarServidor("El usuario '" + nombreObjetivo + "' aún no ha jugado partidas registradas.");
                return;
            }

            double porcentajeVictorias = (stats.victorias / (double) totalPartidas) * 100;
            double porcentajeDerrotas = (stats.derrotas / (double) totalPartidas) * 100;
            double porcentajeEmpates = (stats.empates / (double) totalPartidas) * 100;

            String mensajePuntos = String.format(
                    "\n--- Estadísticas de %s ---\n" +
                            "Puntos Totales: %d\n" +
                            "Victorias (2 pts): %d (%.2f%%)\n" +
                            "Empates (1 pt): %d (%.2f%%)\n" +
                            "Derrotas (0 pts): %d (%.2f%%)\n" +
                            "Total Partidas: %d\n" +
                            "----------------------------------",
                    nombreObjetivo, stats.puntos, stats.victorias, porcentajeVictorias,
                    stats.empates, porcentajeEmpates, stats.derrotas, porcentajeDerrotas, totalPartidas);

            enviarServidor(mensajePuntos);
            return;
        }

        if (comando.equals("/jugar")) {
            enviarServidor("Usa /gato <usuario> para invitar a alguien a una partida de 'No Viudo'.");
            enviarServidor("Usa /vs <u1> <u2> para comparar estadísticas de dos usuarios."); // Nuevo comando añadido aquí
            enviarServidor("Usa /ranking para ver el ranking general.");
            enviarServidor("Usa /puntos <usuario> para ver tus estadísticas o las de otro.");
            enviarServidor("Usuarios conectados: " + ServidorMulti.clientes.keySet());
            return; }
        if (comando.equals("/bloquear") || comando.equals("/desbloquear")) {
            if (this.esInvitado) {
                this.salida.writeUTF("<<SERVIDOR>>: No puedes usar " + comando + " en MODO INVITADO. Inicia sesión o regístrate.");
                this.salida.flush();
                return; }
            if (partes.length != 2) {
                this.salida.writeUTF("<<SERVIDOR>>: Uso incorrecto. Debe ser: " + comando + " <nombre_usuario>");
                this.salida.flush();
                return; }

            String nombreObjetivo = partes[1].trim();

            if (nombreObjetivo.equalsIgnoreCase(this.nombreCliente)) {
                this.salida.writeUTF("<<SERVIDOR>>: No puedes bloquearte a ti mismo.");
                this.salida.flush();
                return; }
            if (!ServidorMulti.usuariosRegistrados.containsKey(nombreObjetivo)) {
                this.salida.writeUTF("<<SERVIDOR>>: Error, el usuario '" + nombreObjetivo + "' no existe para ser bloqueado/desbloqueado.");
                this.salida.flush();
                return; }

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
                    this.salida.writeUTF("<<SERVIDOR>>: El usuario '" + nombreObjetivo + "' no estaba bloqueado.");}
            }
            if (operacionExitosa) {
                this.salida.writeUTF("<<SERVIDOR>>: Usa '@u1 Mensaje' o '@u1,u2 Mensaje' para enviar privados.");
                this.salida.writeUTF("<<SERVIDOR>>: Usa '/bloquear <usuario>' y '/desbloquear <usuario>' para gestionar bloqueos."); }

            this.salida.flush();
            return;
        }
    }
    private boolean manejarMensajeInvitado(String mensaje) throws IOException {
        if (!esInvitado) return false;

        String comando = mensaje.trim().toLowerCase();
        if (comando.equals("/iniciar") || comando.equals("/registro") || comando.startsWith("/bloquear") || comando.startsWith("/desbloquear") || comando.startsWith("/gato") || comando.startsWith("/aceptar") || comando.startsWith("/rechazar") || comando.startsWith("/si") || comando.startsWith("/no") || comando.startsWith("/jugar") || comando.startsWith("/ranking") || comando.startsWith("/puntos") || comando.startsWith("/vs")) {
            return true;
        }

        if (mensajesGratisRestantes <= 0) {
            this.salida.writeUTF("<<SERVIDOR>>: Agotaste tus 3 mensajes gratis. Escribe '/iniciar' o '/registro' para continuar.");
            this.salida.flush();
            return true; }
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
                        continue; }
                    if (ManejadorBloqueos.estaBloqueadoPor(remitente, nombreLimpio)) {
                        this.salida.writeUTF("<<SERVIDOR>>: Tu mensaje a '" + nombreLimpio + "' no fue entregado. Lo tienes bloqueado.");
                        enviado = true;
                        continue;}

                    cliente.salida.writeUTF(mensajeAGrupo);
                    cliente.salida.flush();
                    enviado = true;
                }
            }

            if (!enviado) { System.err.println("Advertencia: No se encontró a ningún destinatario para: " + listaDestinatarios);
                this.salida.writeUTF("<<SERVIDOR>>: Error, no se encontró a los destinatarios: " + listaDestinatarios);
                this.salida.flush();}

        } else { this.salida.writeUTF("<<SERVIDOR>>: Formato de mensaje privado incorrecto. Debe ser: @nombre Mensaje.");
            this.salida.flush();}
    }

    private void transmitirMensajePublico(String mensajeConRemitente) throws IOException {
        String remitente = this.nombreCliente;

        for (UnCliente cliente : ServidorMulti.clientes.values()){
            if (cliente != this) {

                if (ManejadorBloqueos.esUsuarioBloqueado(remitente, cliente.nombreCliente)) {
                    continue; }
                if (ManejadorBloqueos.estaBloqueadoPor(remitente, cliente.nombreCliente)) {
                    continue; }
                cliente.salida.writeUTF(mensajeConRemitente);
                cliente.salida.flush();
            }
        }
    }

    private void manejarDesconexion() {
        System.out.println(this.nombreCliente + " se ha desconectado.");
        ServidorMulti.clientes.remove(this.nombreCliente);

        if (!this.juegosActivos.isEmpty()) {
            for (Map.Entry<String, JuegoGato> entry : this.juegosActivos.entrySet()) {
                JuegoGato juego = entry.getValue();
                UnCliente oponente = ServidorMulti.clientes.get(entry.getKey());
                try {
                    juego.forzarVictoria(this, oponente);
                } catch (IOException e) {
                    System.err.println("Error al notificar victoria por desconexión en juego con " + entry.getKey());
                }
                if (oponente != null) {
                    oponente.juegosActivos.remove(this.nombreCliente); // Limpiar el mapa del oponente
                }
            }
            this.juegosActivos.clear();
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

                if (mensaje.startsWith("/")) {
                    manejarComandos(mensaje);
                    continue;
                }

                if (!this.juegosActivos.isEmpty() && !esInvitado) {

                    String[] partes = mensaje.trim().split(" ", 2);

                    if (this.juegosActivos.size() > 1 && partes.length == 2 && this.juegosActivos.containsKey(partes[0].trim())) {
                        String nombreOponente = partes[0].trim();
                        JuegoGato juego = this.juegosActivos.get(nombreOponente);
                        try {
                            String[] coords = partes[1].trim().split(",");
                            int fila = Integer.parseInt(coords[0].trim());
                            int col = Integer.parseInt(coords[1].trim());

                            juego.mover(this, fila, col);

                        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                            enviarServidor("Formato de jugada incorrecto. Usa 'Oponente Fila,Columna' (ej: Pepe 1,2).");
                        }
                        continue;

                    } else if (this.juegosActivos.size() == 1) {
                        JuegoGato juegoUnico = this.juegosActivos.values().iterator().next();
                        try {
                            String[] coords = mensaje.trim().split(",");
                            int fila = Integer.parseInt(coords[0].trim());
                            int col = Integer.parseInt(coords[1].trim());

                            juegoUnico.mover(this, fila, col);

                        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                            enviarServidor("Formato de jugada incorrecto. Usa 'Fila,Columna' (ej: 1,2).");
                        }
                        continue;

                    } else if (this.juegosActivos.size() > 1 && !this.juegosActivos.containsKey(partes[0].trim())) {
                        enviarServidor("Tienes múltiples partidas activas. Para jugar, usa el formato: Oponente Fila,Columna (ej: Pepe 1,2).");
                    }
                }

                String mensajeConRemitente = this.nombreCliente + ": " + mensaje;

                if (manejarMensajeInvitado(mensaje)) { continue; }

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
