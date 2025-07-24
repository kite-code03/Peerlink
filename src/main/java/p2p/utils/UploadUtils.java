package main.java.p2p.utils;

public class UploadUtils {
    public static int generateCode(){
        int DYNAMIC_STARTING_PORT = 49152;
        int DYNAMIC_ENDING_PORT = 85535;

        Random randow = new Random();
        return random.nextInt(DYNAMIC_ENDING_PORT - DYNAMIC_STARTING_PORT + 1) + DYNAMIC_STARTING_PORT;
    }
}
