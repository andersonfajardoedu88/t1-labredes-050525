import java.util.Scanner;

public class App {
    public static void main(String[] args) throws Exception {
        //Comunicador node = new Comunicador("Device1", 5555);
        Comunicador node = new Comunicador("Device2", 5556);
        
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
        System.out.println("Digite comandos:");
        while (true) {
            String cmd = sc.nextLine();
            if (cmd.equals("heartbeat")) {
                node.sendHeartbeat();
                System.out.println("Heartbeat enviado.");
            }
        }
    }
}
