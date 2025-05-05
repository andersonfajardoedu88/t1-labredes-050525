public class Mensagem {
    String type;
    String id;
    String content;

    public Mensagem(String type, String id, String content) {
        this.type = type;
        this.id = id;
        this.content = content;
    }

    @Override
    public String toString() {
        return type + " " + id + " " + content;
    }
}
