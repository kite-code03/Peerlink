package p2p;

public class App {
    public static void main(String[] args) {
        try{
            FileController fileController = new FileController(8080);
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
