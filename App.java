import java.util.Scanner;

public class App {
    public static void main(String[] args) throws Exception {
        //Comunicador node = new Comunicador("Device1", 5555);
        Comunicador node = new Comunicador("DeviceAnderson", 5556);
        
        new Thread(() -> {
            while (true) {
                try {
                    node.listen();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        Scanner sc = new Scanner(System.in);
        System.out.println("------ INICIO EXECUÇÃO --------");
        System.out.println("Lista de comandos:");
        System.out.println("-heartbeat");
        System.out.println("-devices");
        System.out.println("-talk <device> <mensagem>");
        System.out.println("-sendfile <device> <arquivo>");
        System.out.println("-------------------------------");
        while (true) {
            System.out.println("Digete um novo comando:");
            String cmd = sc.nextLine();
            if (cmd.equalsIgnoreCase("heartbeat")) {
                node.sendHeartbeat();
                System.out.println("Heartbeat enviado.");
            } else if (cmd.equalsIgnoreCase("devices")) {
                node.listarDispositivos();
            } else if (cmd.startsWith("talk ")) {
                String[] partes = cmd.split(" ", 3);
                if (partes.length < 3) {
                    System.out.println("Uso: talk <nome> <mensagem>");
                } else {
                    String nome = partes[1];
                    String mensagem = partes[2];
                    node.enviarMensagemPara(nome, mensagem);
                }
            }else if (cmd.startsWith("sendfile ")) {
                String[] partes = cmd.split(" ", 3);
                if (partes.length < 3) {
                    System.out.println("Uso: sendfile <nome> <caminho-do-arquivo>");
                } else {
                    String nome = partes[1];
                    String caminho = partes[2];
                    node.enviarArquivoPara(nome, caminho);
                }
            }else if (cmd.equalsIgnoreCase("exit")) {
                    sc.close();
                    System.out.println("Encerrando a aplicação...");
                    System.exit(0);
                }
        }
    }
}