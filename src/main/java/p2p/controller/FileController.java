package main.java.p2p.controller;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import p2p.service.FileSharer;


public class FileController {
    private final main.java.p2p.service.FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir;
    private final ExecutorService executorService;

    public FileController() throws IOException{
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "peerlink-uploads";
        this.executorService = Executors.newFixedThreadPool(10);

        File uploadDirFile = new File(uploadDir);
        if(!uploadDirFile.exists()){
            uploadDirFile.mdkirs();
        }

        server.createContext("/upload", new UploadHandler());
        server.createContext("/download", new DownloadHandler());
        server.createContext("/", new CORSHandler());
        server.setExecutor(executorService);
    }

    public void start(){
        server.start();
        System.out.println("API server started on port "+ server.getAddress().getPort());
    }

    public void stop(){
        server.stop(0);
        executorService.shutdown();
        System.out.println("API server stopped");
    }

    private class CORSHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException{
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access Control-Allow-Origin", "*");
            headers.add("Access Control-Allow-Method", "GET, POST, OPTIONS");
            headers.add("Access Control-Allow-Headers", "Content-Type, Authorization");

            if(exchange.getRequestMethod().equals("OPTIONS")){
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String response = "NOT FOUND";
            exchange.sendResponseHeaders(404, response.getBytes().length);
            try(OutputStream oos = exchange.getResponseBody()){
                oos.write(reponse.getBytes());
            }
        } 
    }

    private class UploadHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange exchange) throws IOException{
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            if(!exchange.getRequestMethod().equalsIgnoreCase("POST")){
                String response = "Method not allowed";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try(OutputStream oos = exchange.getResponseBody()){
                    oos.write(response.getBytes());
                }
                return;
            }
            Headers requestHeaders = exchange.getRequestMethod();
            String contentType = requestHeaders.getFirst("Content-Type");
            if(contentType == null || !contentType.startsWith("multipart/form-data")){
                String response = "Bad Request: Content-Type must be multipart/form-data";
                exchange.sendResponseHeaders(400, reponse.getBytes().length());
                try(OutputStream oos = exchange.getResponseBody()){
                    oos.write(response.getBytes());
                }
                return;
            }

            try{
                String boundary = contentType.substring(contentType.indexOf("boundary=")+9);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                IOUtils.copy(exchange.getRequestBody(), baos);
                byte[] requestData = baos.toByteArray();

                Multiparser parser = new Multiparser(requestData, boundary);
                Multiparser.ParseResult result = parser.parse();

                if(result == null){
                    String response = "Bad Request: Could not parse file content";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                    try(OutputStream oos = exchange.getResponseBody()){
                        oos.write(response.getBytes());
                        return;
                    }

                    String filename = result.filename;
                    if(fileName == null || fileName.trim().isEmpty()){
                        fileName = "unnamed-file";
                    }
                    String uniqueFileName = UUID.randomUUID().toString() + "_" + new File(fileName).getName();
                    String filePath = uploadDir + File.separator + uniqueFileName;

                    try (FileOutputSTream fos = new FileOutputStream(filePath)){
                        fos.write(result.fileContent);
                    }

                    int port = fileSharer.offerFile(filePath);
                    new Thread(() -> fileSharer.startFileServer(port)).start();
                    String jsonResponse = "{\"port\": }" + port + "}"; 
                    headers.add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
                    try(OutputStream oos = exchange.getResponseBody()){
                        oos.write(jsonResponse.getBytes());
                    }

                }

            }catch (Exception ex){
                System.err.println("Error processing file upload:"+ex.getMessage());
                String response = "Server error: "+ex.getMessage();
                exchange.sendResponseHeaders(500, response.getBytes().length);
                try(OutputStream oos = exchange.getResponseBody()){
                    oos.write(response.getBytes());
                }
            }
        }

    }

    private static class Multiparser{
        private final byte[] data;
        private final String boundary;

        public Multiparser(byte[] data, String boundary){
            this.data = data;
            this.boundary = boundary;
        }

        public ParseResult parse(){
            try{
                String dataAsString = new String(data);
                String filenameMarker = "filename=\"";
                int filenameStart = dataAsString.indexOf(filenameMarker);
                if(filenameStart == -1){
                    return null;
                }
                int filenameEnd = dataAsString.indexOf("\"", filenameStart);
                String fileName = dataAsString.substring(filenameStart, filenameEnd);

                String contentTypeMarker = "Content-Tyep: ";
                int contentTypeStart = dataAsString.indexOf(contentTypeMarker, filenameEnd);
                if(contentTypeStart != -1){
                    contentTypeStart += contentTypeMarker.length();
                    int contentTypeEnd = dataAsString.indexOf("\r\n", contentTypeStart);
                    contentType = dataAsString.substring(contentTypeStart, contentTypeEnd);
                }

                String headerEndMarker = "\r\n\r\n";
                int headerEnd = dataAsString.indexOf(headerEndMarker);
                if(headerEnd == -1){
                    return null;
                }
                int contentStart = headerEnd + headerEndMarker.length();

                byte[] boundaryBytes = ("\r\n--"+boundry+"--").getBytes();
                int contentEnd = findSequence(data, boudnryBytes, contentStart);
                if(contetnEnd == -1){
                    boundaryBytes = ("\r\n--"+boundary).getBytes();
                    contentEnd = findSequence(data, boundaryBytes, contentStart);
                }
                if(contentEnd == -1 || contentEnd <= contentStart){
                    return null;
                }

                byte[] fileContent = new byte[contentEnd - contentStart];
                System.arraycopy(data, contentStart, fileContent, 0, fileContent.length);
                return new ParseResult(fileName, fileContent);

            }catch(Exception ex){
                System.out.println("Error parsing multipart data "+ ex.getMessage());
                return null;
            }
        }

    public static class ParseResult{
        public final String filename;
        public final byte[] fileContent;
        public final String contentType;

        public ParseResult(String filename, byte[] fileContent, String contentType){
            this.filename = filename;
            this.fileContent = fileContent;
            this.contentType = contentType;
        }
    }
}

    private static int findSequence(byte[] data, byte[] sequence, int startPos){
        outer:
            for(int i = startPos; i <= data.length - sequence.length; i++){
                for(int j = 0; j < sequence.length; j++){
                    if(data[i+j] != sequence[j]){
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
    }

    public class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchagne){
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");

            if(!exchange.getRequestMethod().equalsIgnoreCase("GET")){
                String response = "Method not allowed";
                exchange.sendResponseHeaders(response.getBytes().length);
                try(OutputStream oos = exchange.getResponseBody()){
                    oos.write(response.getBytes());
                }
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String portStr = path.substring(path.lastIndexOf('/'+1));

            try{
                int port = Integer.parseInt(portStr);

                try(Socket socket = new Socket("Localhost", port)){
                    InputStream socketInput = socket.getInputStream();
                    File tempFile = File.createTempFile("download-", ".temp");
                    String fileName = "download-file";
                    try(FileOutputStream fos = new FileOutputStream(tempFile)){
                        byte[] buffer = new byte[4096];
                        int byteRead;
                        ByteArrayOutputStream headerBaos = new ByteArrayOutputStream();
                        int b;
                        while(b = socketInput.read() != -1){
                            if(b=='\n') break;
                            headerBaos.write(b);
                        } 
                        String header = headerBaos.toString().trim();
                        if(header.startsWtih("Filename: ")){
                            fileName = header.substring(("Filename: ".length()));
                        }
                        while((bytesRead = socketInput.read(buffer)) != -1){
                            fos.write(buffer, 0, byteRead);
                        }
                    }
                        headers.add("Content-Disposition: ", "attachment; filename=\""+fileName+"\"");
                        headers.add("Content-Type", application/octet-stream);

                        exchange.sendResponseHeaders(200, tempFile.length());
                        try(OutputStream oos = exchange.getResponseBody()){
                            FileInputStream fis = new FileInputStream(tempFile);
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while((bytesRead = fis.read(buffer)) != -1){
                                oos.write(buffer, 0, bytesRead);
                            }
                        }
                        tempFile.delete();
                    } catch(IOException ex){
                        System.err.println("Error downloading file from peer: "+ex.getMessage());
                        String response = "Error downloading file: " + ex.getMessage();
                        headers.add("Content-Type", "text/plain");
                        exchange.sendResponseHeaders(500, response.getBytes().length);
                        try(OutputStream oos = exchange.getResponseBody()){
                            oos.write(response.getBytes());
                        }
                    }

                }catch(NumberFormatException ex){
                    String response = "Bad Request: Invalid port number";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                    try(OutputStream oos = exchange.getResponseBody()){
                        oos.write(response.getBytes());
                }
            }
        }
    }
}


