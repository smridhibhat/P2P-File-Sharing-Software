package edu.ufl.cise.message;

import java.io.ObjectOutputStream;

public class Handshake {

    public static final String handshakeHeader = "P2PFILESHARINGPROJ";
    public static void sendHandshake(int peerId, ObjectOutputStream out) {
        byte[] result = new byte[32];
        byte[] header = handshakeHeader.getBytes();
        byte[] idArray = Utility.convertToByteArray(peerId);
        for (int i = 0; i < 32; i++) {
            if (i < 18) {
                result[i] = header[i];
            } else if (i > 27) {
                result[i] = idArray[i - 28];
            } else {
                result[i] = 0;
            }
        }
        SendMessage.pushMessage(result, out);
    }

    public static String getHandshakeHeader(byte[] handshake) {
        byte[] header = new byte[18];
        System.arraycopy(handshake, 0, header, 0, 18);
        return new String(header);
    }

    public static int getHandshakePeerId(byte[] handshake) {
        byte[] peerId = new byte[4];
        System.arraycopy(handshake, 28, peerId, 0, 4);
        return Utility.convertToInt(peerId);
    }
}
