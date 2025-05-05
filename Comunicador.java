import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Comunicador {
    DatagramSocket socket;
    int port;
    String name;
    Map<String, Dispositivo> dispositivosAtivos = new ConcurrentHashMap<>();

    public Comunicador(String name, int port) throws Exception {
        this.name = name;
        this.port = port;
        this.socket = new DatagramSocket(port);
        this.socket.setBroadcast(true);

        // Thread que remove dispositivos que estão inativos há mais de 10 segundos
        new Thread(() -> {
            while (true) {
                long agora = System.currentTimeMillis();
                dispositivosAtivos.values().removeIf(d -> agora - d.lastHeartbeat > 10000);
                try {
                    Thread.sleep(5000); // limpa a lista a cada 5 segundos
                } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    public void sendHeartbeat() throws Exception {
        String heartbeat = "HEARTBEAT " + name;
        DatagramPacket packet = new DatagramPacket(
            heartbeat.getBytes(),
            heartbeat.length(),
            InetAddress.getByName("255.255.255.255"), port);
        socket.send(packet);
    }

    public void listen() throws Exception {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        String msg = new String(packet.getData(), 0, packet.getLength());
        String[] parts = msg.split(" ", 2);
        if (parts.length < 2) return;

        String tipo = parts[0];
        String conteudo = parts[1];

        if ("HEARTBEAT".equalsIgnoreCase(tipo)) {
            String nomeDispositivo = conteudo.trim();
            dispositivosAtivos.put(nomeDispositivo, new Dispositivo(
                nomeDispositivo,
                packet.getAddress(),
                packet.getPort(),
                System.currentTimeMillis()
            ));
        }

        System.out.println("Recebido de " + packet.getAddress().getHostAddress() + ": " + msg);
    }
    // Comando devices adicionado
    public void listarDispositivos() {
        System.out.println("\nDispositivos ativos:");
        long agora = System.currentTimeMillis();
        dispositivosAtivos.forEach((nome, disp) -> {
            long segundos = (agora - disp.lastHeartbeat) / 1000;
            System.out.printf("- %s | IP: %s | Porta: %d | Último heartbeat há %ds\n",
                nome, disp.address.getHostAddress(), disp.port, segundos);
        });
        System.out.println();
    }

}
