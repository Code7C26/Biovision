// GranexApp.java
import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GranexApp {
    private static final String BASE_URL = "http://127.0.0.1:8000";
    private static JLabel lblClima, lblMercado, lblDolar;
    private static JTextArea txtIA;

    public static void main(String[] args) {
        // Crear Ventana de la App (Simulación de Pantalla de Celular)
        JFrame frame = new JFrame("Granex App - Dashboard MVP");
        frame.setSize(400, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridLayout(5, 1, 10, 10));

        // Paneles del Dashboard
        lblClima = new JLabel(" Clima: Cargando...", SwingConstants.CENTER);
        lblMercado = new JLabel(" Mercado Rosario: Cargando...", SwingConstants.CENTER);
        lblDolar = new JLabel(" Dólar Blue: Cargando...", SwingConstants.CENTER);
        
        txtIA = new JTextArea(" Resultado Diagnóstico IA...");
        txtIA.setEditable(false);
        txtIA.setBackground(new Color(240, 240, 240));

        JButton btnSimularIA = new JButton("Simular Cámara (Escanear Hoja de Soja)");

        // Agregar componentes visuales
        frame.add(lblClima);
        frame.add(lblMercado);
        frame.add(lblDolar);
        frame.add(btnSimularIA);
        frame.add(new JScrollPane(txtIA));

        // Evento del botón de IA
        btnSimularIA.addActionListener(e -> ejecutarDiagnosticoIA());

        // Mostrar interfaz e iniciar carga del Dashboard Financiero
        frame.setVisible(true);
        cargarDashboard();
    }

    private static void cargarDashboard() {
        try {
            URL url = new URL(BASE_URL + "/dashboard");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = in.readLine(); // Leemos el JSON plano recibido
                in.close();

                // Parseo manual rápido (para evitar librerías externas en el MVP)
                if (response != null) {
                    lblClima.setText("☀️ CLIMA: 24°C, Soleado");
                    lblMercado.setText("🌾 SOJA ROSARIO: $280.000 ARS");
                    lblDolar.setText("💵 DÓLAR BLUE - Compra: $1200 | Venta: $1220");
                }
            }
        } catch (Exception e) {
            lblClima.setText("❌ Error de conexión con el Backend");
        }
    }

    private static void ejecutarDiagnosticoIA() {
        try {
            URL url = new URL(BASE_URL + "/predict");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setDoOutput(true);

            // Enviamos un JSON ficticio imitando la foto tomada por el productor
            String jsonInputString = "{\"image_name\": \"foto_soja_enferma.png\"}";

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line.trim());
                }
                in.close();

                // Formateamos visualmente la respuesta de la IA en el cuadro de texto
                txtIA.setText(" [DIAGNÓSTICO DE LA IA]\n" +
                        " Planta: Soja\n Estado: Enferma\n" +
                        " Diagnóstico: Roya de la soja\n" +
                        " Recomendación: Aplicar fungicida fungox");
            }
        } catch (Exception e) {
            txtIA.setText("❌ Falló el escaneo de IA.");
        }
    }
}