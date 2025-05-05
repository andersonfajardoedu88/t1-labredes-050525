import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.Base64;

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
        } else if ("TALK".equalsIgnoreCase(tipo)) {
            String[] partes = conteudo.split(" ", 2);
            if (partes.length == 2) {
                String id = partes[0];
                String msgRecebida = partes[1];
                System.out.println("[Mensagem recebida] " + msgRecebida + " (ID: " + id + ")");
            }
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

    public void enviarMensagemPara(String nomeDestino, String mensagem) throws Exception {
        Dispositivo destino = dispositivosAtivos.get(nomeDestino);
        if (destino == null) {
            System.out.println("Dispositivo \"" + nomeDestino + "\" não encontrado.");
            return;
        }
    
        // Gera um ID simples baseado no tempo
        String id = String.valueOf(System.currentTimeMillis());
        String mensagemCompleta = "TALK " + id + " " + mensagem;
    
        byte[] dados = mensagemCompleta.getBytes();
        DatagramPacket packet = new DatagramPacket(
            dados, dados.length, destino.address, destino.port);
        socket.send(packet);
    
        System.out.println("Mensagem enviada para " + nomeDestino + ": " + mensagem);
    }    

}
