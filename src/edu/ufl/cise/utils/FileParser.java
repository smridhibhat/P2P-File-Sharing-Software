package edu.ufl.cise.utils;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * @author Aryan
 */
public class FileParser {

    public static byte[][] fileFragments;

    public static int getTotalPieces() {
        return (int)Math.ceil((double) CommonConfigParser.getFileSize() / CommonConfigParser.getPieceSize());
    }
    public static void fragment(int ID) throws IOException {
        fileFragments = new byte[getTotalPieces()][CommonConfigParser.getPieceSize()];
        int peerIndex = FileParser.getPeerIndexByID(ID);
        if (peerIndex != -1) {
            String peerFolder = FileParser.initPeerFolder(ID);
            if (PeerInfoConfigParser.peerInfo.get(peerIndex).isFilePresent()) {
                byte[] files = Files.readAllBytes(Paths.get(peerFolder + CommonConfigParser.getFileName()));
                int total = getTotalPieces();
                for (int j = 0; j < total - 1; j++) {
                    fileFragments[j] = Arrays.copyOfRange(files, j * CommonConfigParser.getPieceSize(), (j + 1) * CommonConfigParser.getPieceSize());
                }
                fileFragments[total - 1] = Arrays.copyOfRange(files, (total - 1) * CommonConfigParser.getPieceSize(), files.length);
            }
        }
    }

    public static void writeBack(int peerId, String str) {
        int peerIndex = getPeerIndexByID(peerId);
        if (peerIndex != -1) {
            String peerFolder = initPeerFolder(peerId);
            try (FileWriter fileWriter = new FileWriter(peerFolder + CommonConfigParser.getFileName())) {
                fileWriter.write(str);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String initPeerFolder(int peerId) {
        String workDir = System.getProperty("user.dir");
        String peerFolder = workDir + File.separator + "peer_"+peerId + File.separator;
        File dir = new File(peerFolder);
        if (!dir.exists()) dir.mkdirs();
        return peerFolder;
    }

    public static void defragment(int peerId) {
        StringBuilder out = new StringBuilder();
        int total = getTotalPieces();
        for (int j = 0; j < total - 1; j++) {
            out.append(new String(fileFragments[j], StandardCharsets.UTF_8));
        }
        out.append(new String(Arrays.copyOfRange(fileFragments[total - 1], 0, CommonConfigParser.getFileSize() - CommonConfigParser.getPieceSize() * (total - 1)), StandardCharsets.UTF_8));
        writeBack(peerId, out.toString());
    }

    public static int getPeerIndexByID(int peerId) {
        for (int i = 0; i < PeerInfoConfigParser.peerInfo.size(); i++) {
            if (peerId == PeerInfoConfigParser.peerInfo.get(i).getPeerId()) {
                return i;
            }
        }
        return -1;
    }

    public static byte[] getPiece(int pieceIndex) {
        return fileFragments[pieceIndex];
    }
}
