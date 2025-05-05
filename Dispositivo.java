import java.net.InetAddress;

public class Dispositivo {
    String name;
    InetAddress address;
    int port;
    long lastHeartbeat;

    public Dispositivo(String name, InetAddress address, int port, long lastHeartbeat) {
        this.name = name;
        this.address = address;
        this.port = port;
        this.lastHeartbeat = lastHeartbeat;
    }
}
