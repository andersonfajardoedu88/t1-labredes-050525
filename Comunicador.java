import java.net.*;

public class Comunicador {
    DatagramSocket socket;
    int port;
    String name;

    public Comunicador(String name, int port) throws Exception {
        this.name = name;
        this.port = port;
        this.socket = new DatagramSocket(port);
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
        System.out.println("Recebido: " + msg);
    }
}
