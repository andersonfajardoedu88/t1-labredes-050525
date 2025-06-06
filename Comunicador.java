import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.security.MessageDigest;

public class Comunicador {
    DatagramSocket socket;
    int port;
    String name;
    Map<String, Dispositivo> dispositivosAtivos = new ConcurrentHashMap<>();
    
    // Mapa para arquivos em recepção
    Map<String, ArquivoRecebido> arquivosEmRecepcao = new ConcurrentHashMap<>();

    public Comunicador(String name, int port) throws Exception {
        this.name = name;
        this.port = port;
        this.socket = new DatagramSocket(port);
        this.socket.setBroadcast(true);

        // Thread que remove dispositivos que estão inativos há mais de 10 segundos
        new Thread(() -> {
            while (true) {
                long agora = System.currentTimeMillis();
                //dispositivosAtivos.values().removeIf(d -> agora - d.lastHeartbeat > 50000);
                try {
                    Thread.sleep(50000); // limpa a lista a cada 5 segundos
                } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    class ArquivoRecebido {
        String nome;
        int tamanho;
        Map<Integer, byte[]> blocos = new TreeMap<>();
        long recebidoTotal = 0;
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
        }else if ("TALK".equalsIgnoreCase(tipo)) {
            String[] partes = conteudo.split(" ", 2);
            if (partes.length == 2) {
                String id = partes[0];
                String msgRecebida = partes[1];
                System.out.println("[Mensagem recebida] " + msgRecebida + " (ID: " + id + ")");
            }
        }else if ("FILE".equalsIgnoreCase(tipo)) {
            String[] partes = conteudo.split(" ", 3);
            if (partes.length == 3) {
                String id = partes[0];
                String nomeArquivo = partes[1];
                int tamanho = Integer.parseInt(partes[2]);
        
                ArquivoRecebido arq = new ArquivoRecebido();
                arq.nome = nomeArquivo;
                arq.tamanho = tamanho;
                arquivosEmRecepcao.put(id, arq);
        
                System.out.println("Preparado para receber arquivo: " + nomeArquivo + " (" + tamanho + " bytes)");
                String ackMsg = "ACK " + id;
                enviarMensagem(new Dispositivo("", packet.getAddress(), packet.getPort(), 0), ackMsg);
            }
        }else if ("CHUNK".equalsIgnoreCase(tipo)) {
            String[] partes = conteudo.split(" ", 3);
            if (partes.length == 3) {
                String id = partes[0];
                int seq = Integer.parseInt(partes[1]);
                String dadosBase64 = partes[2];
        
                ArquivoRecebido arq = arquivosEmRecepcao.get(id);
                /*if (arq != null) {
                    byte[] dados = Base64.getDecoder().decode(dadosBase64);
                    arq.blocos.put(seq, dados);
                    arq.recebidoTotal += dados.length;
                    System.out.println("Recebido bloco " + seq + " do arquivo " + arq.nome);
                    String ackMsg = "ACK " + id + "-" + seq;
                    enviarMensagem(new Dispositivo("", packet.getAddress(), packet.getPort(), 0), ackMsg);
                }*/
                if (arq == null) {
                    System.out.println("CHUNK ignorado: ID " + id + " não encontrado.");
                    return;
                }
        
                if (arq.blocos.containsKey(seq)) {
                    System.out.println("Bloco duplicado ignorado: " + seq);
                    return;
                }

                byte[] dados = Base64.getDecoder().decode(dadosBase64);
                arq.blocos.put(seq, dados);
                arq.recebidoTotal += dados.length;
        
                System.out.println("Recebido bloco " + seq + " do arquivo " + arq.nome);
        
                String ackMsg = "ACK " + id + "-" + seq;
                enviarMensagem(new Dispositivo("", packet.getAddress(), packet.getPort(), 0), ackMsg);
            }
        }else if ("END".equalsIgnoreCase(tipo)) {
            String[] partes = conteudo.split(" ", 2);
            if (partes.length == 2) {
                String id = partes[0];
                String hashEsperado = partes[1];
        
                ArquivoRecebido arq = arquivosEmRecepcao.get(id);
                
                if (arq == null) {
                    System.out.println("END ignorado: ID " + id + " não encontrado.");
                    return;
                }
        
                if (arq.blocos.isEmpty()) {
                    System.out.println("Erro: nenhum bloco recebido para o arquivo " + arq.nome);
                    return;
                }
        
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (byte[] bloco : arq.blocos.values()) {
                    out.write(bloco);
                }
        
                    byte[] dadosCompletos = out.toByteArray();
                    String hashCalculado = calcularHashSHA256(dadosCompletos);
        
                    if (hashEsperado.equals(hashCalculado)) {
                        System.out.println("Arquivo " + arq.nome + " recebido com sucesso. Salvando...");
                        FileOutputStream fos = new FileOutputStream("recebido_" + arq.nome);
                        fos.write(dadosCompletos);
                        fos.close();
                        System.out.println("Arquivo salvo como: recebido_" + arq.nome);
                        String ackMsg = "ACK " + id + "-end";
                        enviarMensagem(new Dispositivo("", packet.getAddress(), packet.getPort(), 0), ackMsg);
                    } else {
                        System.out.println("Hash inválido. Arquivo corrompido!");
                        String nackMsg = "NACK " + id + "-end Arquivo corrompido (hash diferente)";
                        enviarMensagem(new Dispositivo("", packet.getAddress(), packet.getPort(), 0), nackMsg);
                    }
        
                    arquivosEmRecepcao.remove(id);
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

    public void enviarArquivoPara(String nomeDestino, String caminhoArquivo) throws Exception {
    Dispositivo destino = dispositivosAtivos.get(nomeDestino);
    if (destino == null) {
        System.out.println("Dispositivo \"" + nomeDestino + "\" não encontrado.");
        return;
    }

    File arquivo = new File(caminhoArquivo);
    if (!arquivo.exists()) {
        System.out.println("Arquivo \"" + caminhoArquivo + "\" não encontrado.");
        return;
    }

    String id = String.valueOf(System.currentTimeMillis());
    long tamanho = arquivo.length();
    String nomeArquivo = arquivo.getName();
    String msgFile = "FILE " + id + " " + nomeArquivo + " " + tamanho;

    // Envia mensagem FILE
    enviarMensagem(destino, msgFile);
    System.out.println("Iniciando envio do arquivo: " + nomeArquivo);

    // Envia CHUNKs (partes do arquivo)
    FileInputStream in = new FileInputStream(arquivo);
    byte[] buffer = new byte[512]; // bloco de 512 bytes
    int bytesRead;
    int seq = 0;
    ByteArrayOutputStream totalBytes = new ByteArrayOutputStream();

    while ((bytesRead = in.read(buffer)) != -1) {
        byte[] chunk = Arrays.copyOf(buffer, bytesRead);
        totalBytes.write(chunk);

        String dadosBase64 = Base64.getEncoder().encodeToString(chunk);
        String msgChunk = "CHUNK " + id + " " + seq + " " + dadosBase64;
        enviarMensagem(destino, msgChunk);
        System.out.println("Enviado bloco " + seq);
        seq++;

        Thread.sleep(100); // pequeno atraso para evitar congestionamento
    }

    in.close();

    // Calcula hash SHA-256
    byte[] fileBytes = totalBytes.toByteArray();
    String hash = calcularHashSHA256(fileBytes);
    String msgEnd = "END " + id + " " + hash;
    enviarMensagem(destino, msgEnd);

    System.out.println("Arquivo enviado. Aguardando ACK...");

    }

    private void enviarMensagem(Dispositivo destino, String mensagem) throws IOException {
        byte[] dados = mensagem.getBytes();
        DatagramPacket packet = new DatagramPacket(
            dados, dados.length, destino.address, destino.port);
        socket.send(packet);
    }

    private String calcularHashSHA256(byte[] dados) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(dados);
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}