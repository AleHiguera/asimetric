package Clientemulti;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ClienteMulti {

    public static void main(String[] args) throws IOException {
        BufferedReader tecladoInicial = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            Socket s = null;
            String datosConexion = null;
            String nombreUsuario = null;

            while (datosConexion == null) {
                System.out.println("\n--- Menú de Conexión ---");
                System.out.println("1. Continuar como Invitado (3 mensajes gratis)");
                System.out.println("2. Iniciar Sesión");
                System.out.println("3. Registrarse");
                System.out.print("Elige una opción: ");
                String opcion = tecladoInicial.readLine().trim();

                switch (opcion) {
                    case "1":
                        datosConexion = "INVITADO";
                        break;
                    case "2":
                        System.out.print("Nombre de usuario: ");
                        String usuarioLogin = tecladoInicial.readLine().trim();
                        System.out.print("Contraseña: ");
                        String contrasenaLogin = tecladoInicial.readLine().trim();
                        datosConexion = "INICIO_SESION:" + usuarioLogin + ":" + contrasenaLogin;
                        break;
                    case "3":
                        System.out.print("Nombre de usuario para registrar: ");
                        String usuarioRegistro = tecladoInicial.readLine().trim();
                        System.out.print("Contraseña para registrar: ");
                        String contrasenaRegistro = tecladoInicial.readLine().trim();
                        datosConexion = "REGISTRO:" + usuarioRegistro + ":" + contrasenaRegistro;
                        break;
                    default:
                        System.out.println("Opción no válida. Intenta de nuevo.");
                        break;
                }
            }

            try {
                s = new Socket("localhost", 8080);

                DataOutputStream salidaInicial = new DataOutputStream(s.getOutputStream());
                DataInputStream entradaInicial = new DataInputStream(s.getInputStream());

                salidaInicial.writeUTF(datosConexion);

                String respuestaServidor = entradaInicial.readUTF();
                String[] partesRespuesta = respuestaServidor.split(":", 2);
                String tipoRespuesta = partesRespuesta[0];

                if (tipoRespuesta.startsWith("OK_")) {
                    nombreUsuario = partesRespuesta[1];
                    System.out.println("\n¡Conexión exitosa! Tu nombre es: " + nombreUsuario);

                    ParaMandar paraMandar = new ParaMandar(s);
                    Thread hiloParaMandar = new Thread(paraMandar);
                    hiloParaMandar.start();

                    ParaRecibir paraRecibir = new ParaRecibir(s);
                    Thread hiloParaRecibir = new Thread(paraRecibir);
                    hiloParaRecibir.start();

                    try {
                        hiloParaRecibir.join();
                        hiloParaMandar.interrupt();

                        if (s != null && !s.isClosed()) s.close();

                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }

                } else if (tipoRespuesta.startsWith("ERROR_")) {
                    System.err.println("\n[ERROR DE CONEXIÓN]: " + partesRespuesta[1]);
                    s.close();
                    datosConexion = null;

                } else {
                    System.err.println("\n[ERROR DE CONEXIÓN] Respuesta desconocida del servidor: " + respuestaServidor);
                    s.close();
                    datosConexion = null;
                }
            } catch (IOException e) {
                System.err.println("\n[ERROR GRAVE] No se pudo conectar al servidor. Asegúrese de que el servidor esté en ejecución.");
                datosConexion = null;
                if (s != null && !s.isClosed()) s.close();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
            }
            datosConexion = null;
        }
    }
}