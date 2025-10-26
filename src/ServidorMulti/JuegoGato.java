package ServidorMulti;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
public class JuegoGato {
    private final UnCliente jugadorX;
    private final UnCliente jugadorO;
    private final char[][] tablero;
    private char turnoActual;
    private boolean activo;

    public JuegoGato(UnCliente cliente1, UnCliente cliente2) {
        this.tablero = new char[3][3];
        for (char[] row : tablero) {
            Arrays.fill(row, ' '); }
        this.activo = true;

        Random rand = new Random();
        if (rand.nextBoolean()) {
            this.jugadorX = cliente1;
            this.jugadorO = cliente2;
        } else {
            this.jugadorX = cliente2;
            this.jugadorO = cliente1;
        }

        this.turnoActual = 'X';
    }

    public UnCliente getJugadorX() {
        return jugadorX;
    }

    public UnCliente getJugadorO() {
        return jugadorO;
    }
    private UnCliente getJugadorActual() {
        return (turnoActual == 'X') ? jugadorX : jugadorO;
    }

    private UnCliente getOponente(UnCliente jugador) {
        if (jugador == jugadorX) return jugadorO;
        if (jugador == jugadorO) return jugadorX;
        return null;
    }

    public void iniciarJuego() throws IOException {
        String mensajeInicio = "<<NO VIUDO>>: ¡Juego iniciado! El turno se ha asignado aleatoriamente.";

        jugadorX.salida.writeUTF(mensajeInicio + "\nEres la FICHA 'X'. Empiezas tú.");
        jugadorX.salida.writeUTF(getTableroString());

        jugadorO.salida.writeUTF(mensajeInicio + "\nEres la FICHA 'O'. Espera el movimiento de " + jugadorX.nombreCliente + ".");
        jugadorO.salida.writeUTF(getTableroString());
    }

    public String getTableroString() {
        StringBuilder sb = new StringBuilder("\n--- Tablero del Gato ---\n");
        for (int i = 0; i < 3; i++) {
            sb.append("|");
            for (int j = 0; j < 3; j++) {
                sb.append(" ").append(tablero[i][j]).append(" |");
            }
            sb.append("\n");
        }
        sb.append("------------------------\n");
        sb.append("Turno actual: ").append(getJugadorActual().nombreCliente).append(" (").append(turnoActual).append(").");
        sb.append("\nIngresa tu jugada como: Fila,Columna (ej: 0,2). Si tienes múltiples partidas, usa: Oponente Fila,Columna (ej: Pepe 0,2)."); // Mensaje ajustado
        return sb.toString();
    }

    public void mover(UnCliente jugador, int fila, int col) throws IOException {
        if (!activo) {
            jugador.salida.writeUTF("<<NO VIUDO>>: El juego ha terminado.");
            return;
        }

        char ficha = (jugador == jugadorX) ? 'X' : 'O';
        UnCliente oponente = getOponente(jugador);

        if (jugador != getJugadorActual()) {
            jugador.salida.writeUTF("<<NO VIUDO>>: ¡Espera tu turno! Es el turno de " + getJugadorActual().nombreCliente + ".");
            return;
        }

        if (fila < 0 || fila > 2 || col < 0 || col > 2 || tablero[fila][col] != ' ') {
            jugador.salida.writeUTF("<<NO VIUDO>>: Jugada inválida. Las coordenadas deben ser 0-2 y la casilla debe estar vacía.");
            return;
        }
        tablero[fila][col] = ficha;

        if (verificarGanador(ficha)) {
            notificarFinDeJuego(jugador, oponente, "¡GANADOR!");
        } else if (verificarEmpate()) {
            notificarFinDeJuego(jugador, oponente, "¡EMPATE!");
        } else {
            turnoActual = (ficha == 'X') ? 'O' : 'X';
            jugador.salida.writeUTF("<<NO VIUDO>>: Has movido. Esperando movimiento de " + oponente.nombreCliente + ".");
            oponente.salida.writeUTF("<<NO VIUDO>>: Es tu turno. " + getTableroString());
        }
    }

    private boolean verificarGanador(char ficha) {
        for (int i = 0; i < 3; i++) {
            if (tablero[i][0] == ficha && tablero[i][1] == ficha && tablero[i][2] == ficha) return true;
            if (tablero[0][i] == ficha && tablero[1][i] == ficha && tablero[2][i] == ficha) return true;
        }
        if (tablero[0][0] == ficha && tablero[1][1] == ficha && tablero[2][2] == ficha) return true;
        if (tablero[0][2] == ficha && tablero[1][1] == ficha && tablero[2][0] == ficha) return true;

        return false;
    }

    private boolean verificarEmpate() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (tablero[i][j] == ' ') return false;
            }
        }
        return true;
    }

    public void notificarFinDeJuego(UnCliente ganador, UnCliente perdedor, String resultado) throws IOException {
        activo = false;
        String tableroFinal = getTableroString();

        ganador.salida.writeUTF("<<< " + resultado + " >>>\n" + tableroFinal);
        ganador.salida.writeUTF("<<NO VIUDO>>: La partida ha terminado. ¿Quieres jugar de nuevo contra " + perdedor.nombreCliente + "? Responde: /si o /no");

        if (ganador != perdedor) {
            perdedor.salida.writeUTF("<<< HAS PERDIDO >>>\n" + tableroFinal);
            perdedor.salida.writeUTF("<<NO VIUDO>>: La partida ha terminado. ¿Quieres jugar de nuevo contra " + ganador.nombreCliente + "? Responde: /si o /no");
        }
        ganador.juegosActivos.remove(perdedor.nombreCliente);
        perdedor.juegosActivos.remove(ganador.nombreCliente);

        ServidorMulti.propuestasRejuego.put(ganador.nombreCliente, perdedor.nombreCliente);
        ServidorMulti.propuestasRejuego.put(perdedor.nombreCliente, ganador.nombreCliente);

        System.out.println("Propuestas de re-juego registradas.");

        System.out.println("Partida No Viudo (" + jugadorX.nombreCliente + " vs " + jugadorO.nombreCliente + ") finalizada: " + resultado);
    }
    public void forzarVictoria(UnCliente perdedor, UnCliente ganador) throws IOException {
        if (!activo) return;
        activo = false;

        if (ganador != null) {
            ganador.salida.writeUTF("<<< ¡VICTORIA POR ABANDONO! >>>");
            ganador.salida.writeUTF("<<NO VIUDO>>: Tu oponente (" + perdedor.nombreCliente + ") se ha desconectado. ¡Ganas automáticamente!");
            ganador.salida.writeUTF("<<NO VIUDO>>: ¿Quieres iniciar una nueva partida con alguien más? Usa /gato <usuario>.");
            ganador.juegosActivos.remove(perdedor.nombreCliente);
        }
    }
}