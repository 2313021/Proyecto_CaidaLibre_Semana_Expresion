import com.fazecast.jSerialComm.SerialPort;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class RealTimeGraph extends JFrame {

    private XYSeries series;
    private XYSeriesCollection dataset;
    private ChartPanel chartPanel;
    private SerialPort comPort;

    public RealTimeGraph(String title) {
        super(title);

        // Crear una serie de datos XY
        series = new XYSeries("Aceleración");

        // Crear un conjunto de datos XY
        dataset = new XYSeriesCollection(series);

        // Crear el gráfico
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Gráfico de Aceleración en Tiempo Real",
                "Tiempo",
                "Aceleración",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // Configurar el rango del eje Y para que sea dinámico
        NumberAxis yAxis = (NumberAxis) chart.getXYPlot().getRangeAxis();
        yAxis.setAutoRangeIncludesZero(false);

        // Crear el panel de gráfico
        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(800, 600));
        setContentPane(chartPanel);

        // Agregar un shutdown hook para cerrar el puerto serial
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (comPort != null && comPort.isOpen()) {
                comPort.closePort();
            }
        }));
    }

    // Método para listar puertos seriales disponibles
    private void listAvailablePorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        System.out.println("Puertos seriales disponibles:");
        for (int i = 0; i < ports.length; i++) {
            System.out.println((i + 1) + ": " + ports[i].getSystemPortName());
        }
    }

    // Método para seleccionar el puerto serial
    private SerialPort selectPort() {
        SerialPort[] ports = SerialPort.getCommPorts();
        listAvailablePorts();
        int chosenPortIndex = -1;

        while (chosenPortIndex < 0 || chosenPortIndex >= ports.length) {
            String input = JOptionPane.showInputDialog(null, "Selecciona el puerto (1-" + ports.length + "):");
            try {
                chosenPortIndex = Integer.parseInt(input) - 1;
            } catch (NumberFormatException e) {
                System.err.println("Entrada inválida. Por favor ingresa un número.");
            }
        }

        return ports[chosenPortIndex];
    }

    // Método para iniciar la lectura de datos desde el puerto serial
    public void startReadingData() {
        comPort = selectPort();  // Selecciona el puerto serial
        comPort.setBaudRate(9600);
        comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 2000, 0); // Incrementar el tiempo de espera

        if (!comPort.openPort()) {
            System.err.println("No se pudo abrir el puerto: " + comPort.getSystemPortName());
            System.err.println("Verifica que el puerto no está siendo utilizado por otra aplicación.");
            return;
        }

        System.out.println("Puerto abierto: " + comPort.getSystemPortName());

        try (BufferedReader input = new BufferedReader(new InputStreamReader(comPort.getInputStream()))) {
            String line;
            int time = 0;
            while (true) {
                line = input.readLine();
                if (line != null && !line.isEmpty()) {
                    System.out.println("Datos recibidos: " + line); // Mensaje de depuración
                    String[] data = line.split(",");
                    if (data.length == 4) {
                        try {
                            double acceleration = Double.parseDouble(data[3]); // No es necesario multiplicar por 10
                            addData(time++, acceleration);
                        } catch (NumberFormatException e) {
                            System.err.println("Error al parsear los datos: " + e.getMessage());
                        }
                    } else {
                        System.err.println("Datos recibidos en formato incorrecto: " + line);
                    }
                } else {
                    System.out.println("Esperando datos..."); // Mensaje de depuración
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (comPort != null && comPort.isOpen()) {
                comPort.closePort();
                System.out.println("Puerto cerrado.");
            }
        }
    }

    // Método para agregar datos al gráfico
    private void addData(double x, double y) {
        SwingUtilities.invokeLater(() -> {
            series.add(x, y);
            System.out.println("Dato agregado al gráfico: (" + x + ", " + y + ")"); // Depuración
            chartPanel.repaint(); // Repintar la gráfica después de agregar los datos
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            RealTimeGraph demo = new RealTimeGraph("Gráfico en Tiempo Real");
            demo.pack();
            demo.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            demo.setVisible(true);

            // Iniciar la lectura de datos en un hilo separado
            new Thread(demo::startReadingData).start();
        });
    }
}
