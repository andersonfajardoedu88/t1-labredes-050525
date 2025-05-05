public class Menssagem {
    String type;
    String id;
    String content;

    public Message(String type, String id, String content) {
        this.type = type;
        this.id = id;
        this.content = content;
    }

    @Override
    public String toString() {
        return type + " " + id + " " + content;
    }
}
