import java.util.ArrayList;

public class Room {
    private final String name;
    private final ArrayList<Client> clients = new ArrayList<>();

    public Room(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    public ArrayList<Client> getClients() {
        return clients;
    }

    public void joinRoom(Client client) {
        clients.add(client);
    }

    public void leaveRoom(Client client) {
        clients.remove(client);
    }
}
