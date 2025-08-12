package p2p;

import main.java.p2p.controller.FileController;

public class App {
    public static void main(String[] args) {
        try{
            int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
            FileController fileController = new FileController(port);

            fileController.start();
            System.out.println("Peerlink server started on port 8080");
            System.out.println("UI available at http://localhost:3000");
            Runtime.getRuntime().addShutdownHook(
                new Thread(
                    () -> {
                        System.out.println("Shutting down the server");
                        fileController.stop();
                    }
                )
            );
        }catch(Exception ex){
            System.err.println("Failed to start the server at port 8080: ");
            ex.printStackTrace();
        }
    }
}
