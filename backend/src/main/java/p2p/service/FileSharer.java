package main.java.p2p.service;

import main.java.p2p.utils.UploadUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class FileSharer {

    private final HashMap<Integer, String> availableFiles;

    public FileSharer() {
        availableFiles = new HashMap<>();
    }

    // Offer a file and return the unique port assigned
    public int offerFile(String filePath) {
        int port;
        while (true) {
            port = UploadUtils.generateCode(); // Generates a random port
            if (!availableFiles.containsKey(port)) {
                availableFiles.put(port, filePath);
                return port;
            }
        }
    }

    // Start a file server for the given file
    public void startFileServer(int port) {
        String filePath = availableFiles.get(port);
        if (filePath == null) {
            System.out.println("No file found for port: " + port);
            return;
        }

        // Bind server socket to 0.0.0.0 for public access
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            File file = new File(filePath);
            System.out.println("Serving file: " + file.getName() + " on port: " + port);

            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected: " + clientSocket.getInetAddress());

            // Send the file in a new thread
            new Thread(new FileSenderHandler(clientSocket, filePath)).start();

        } catch (IOException ex) {
            System.err.println("Error handling file server on port: " + port + " - " + ex.getMessage());
        }
    }

    // Inner class to handle sending the file
    private static class FileSenderHandler implements Runnable {

        private final Socket clientSocket;
        private final String filePath;

        public FileSenderHandler(Socket clientSocket, String filePath) {
            this.clientSocket = clientSocket;
            this.filePath = filePath;
        }

        @Override
        public void run() {
            try (FileInputStream fis = new FileInputStream(filePath);
                 OutputStream oos = clientSocket.getOutputStream()) {

                String fileName = new File(filePath).getName();
                String header = "Filename: " + fileName + "\n";
                oos.write(header.getBytes());

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    oos.write(buffer, 0, bytesRead);
                }

                System.out.println("File " + fileName + " sent to " + clientSocket.getInetAddress());

            } catch (Exception ex) {
                System.out.println("Error sending file to the client: " + ex.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (Exception ex) {
                    System.err.println("Error closing socket: " + ex.getMessage());
                }
            }
        }
    }
}
