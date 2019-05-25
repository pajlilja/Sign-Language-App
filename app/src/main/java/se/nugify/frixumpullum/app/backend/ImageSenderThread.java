package se.nugify.frixumpullum.app.backend;

import android.os.Handler;
import android.renderscript.ScriptGroup;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import com.jcraft.jsch.*;

import se.nugify.frixumpullum.app.backend.util.ResponseCallback;
import se.nugify.frixumpullum.frixumpullum.R;

/**
 * Thread to send images to the given server
 *
 * @author Marcus Östling, Gustav Nelson Schneider
 */
public class ImageSenderThread extends Thread {

    private Queue<byte[]> queue = new LinkedList<>();
    private boolean open = true;

    private String serverAddress;
    private int serverPort;
    private String response = "";

    //private int fileCounter = 0;
    private String path = "/home/tmp-se/Documents/SE/slr/";
    //private String path = "~/";
    private String fileName = "";
    private String fileExtension = ".jpg";
    private String userName = "tmp-se";
    private String password = "YMuf0cMop@nX";
    private String serverScriptName = "recognize.sh";

    private int responseTimeout = 30000;

    private java.util.Properties config = new java.util.Properties();

    private ArrayList<ResponseCallback> responseListeners = new ArrayList<>();

    /**
     * ImageSenderThread
     *
     * @param serverAddress String with server address
     * @param serverPort    int with server port
     */
    public ImageSenderThread(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;

        this.config.put("StrictHostKeyChecking", "no");
        this.config.put("PreferredAuthentications", "password");
    }

    /**
     * Sends images from the queue to the server
     */
    @Override
    public void run() {
        int counter = 0;
        int timeoutCount = 0;
        while (this.open || !queue.isEmpty()) {
            if (queue.isEmpty()) {
                continue;
            }
            byte[] img = queue.peek();
            int res = sendFile(this.fileName + counter + this.fileExtension, img);
            if (res == 0) {
                counter++;
                timeoutCount = 0;
                queue.remove();
            } else if (res == -2) {
                handleOnNoConnection();
                return;
            } else {
                timeoutCount++;
                if (timeoutCount > 5) {
                    handleOnNoConnection();
                    return;
                }
            }

        }

        runServerScript();
        //listen for response
        long startTime = System.currentTimeMillis();
        String result = null;
        while (startTime + this.responseTimeout > System.currentTimeMillis()) {
            result = getResultFile();
            if (result != null)
                break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
        if (result != null) {
            Log.w("ImageSenderThread", "Result: " + result);
        } else {
            Log.w("ImageSenderThread", "Couln't fetch result");
        }

        handleOnResponse(result);
    }


    private void closeConnection(Session s, Channel c, InputStream in, OutputStream out) throws IOException {
        //in.close();
        out.close();
        c.disconnect();
        s.disconnect();
    }

    private int sendFile(String name, byte[] img) {

        JSch jsch = new JSch();
        Session session;

        Log.w("ImageSenderThread", "New image, index: " + name + " size: " + img.length);

        try {

            session = jsch.getSession(userName, this.serverAddress, serverPort);

            //session.setConfig("compression.s2c", "zlib@openssh.com,zlib,none");
            //session.setConfig("compression.c2s", "zlib@openssh.com,zlib,none");
            //session.setConfig("compression_level", "9");

            session.setConfig(this.config);
            session.setPassword(this.password);
            session.connect();

            String command = "scp -C -t " + path + "img/" + name;
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            if (checkAck(in) != 0) {
                Log.w("ImageSenderThread", "First checkAck failed");
                closeConnection(session, channel, in, out);
                return -1;
            }

            // send "C 0644 filesize filename", where filename should not include '/'
            int filesize = img.length;

            command = "C0644 " + filesize + " ";
            command += name;
            command += "\n";

            out.write(command.getBytes());
            out.flush();

            if (checkAck(in) != 0) {
                Log.w("ImageSenderThread", "Second checkAck failed");
                closeConnection(session, channel, in, out);
                return -1;
            }

            // send a content of image
            int imgIndex = 0;
            int sizeLeft = filesize;
            while (sizeLeft > 0) {
                int chunckSize = sizeLeft < 1024 ? sizeLeft : 1024;
                out.write(img, imgIndex, chunckSize);
                imgIndex += chunckSize;
                sizeLeft -= chunckSize;
            }
            out.flush();

            // send '\0'
            out.write(0);
            out.flush();

            if (checkAck(in) != 0) {
                Log.w("ImageSenderThread", "Third checkAck failed");
                closeConnection(session, channel, in, out);
                return -1;
            }

            closeConnection(session, channel, in, out);
        } catch (JSchException e) {
            e.printStackTrace();
            return -2;
        } catch (IOException e) {
            e.printStackTrace();
            return -2;
        } catch (Exception e) {
            e.printStackTrace();
            return -2;
        }
        return 0;
    }

    private int runServerScript() {
        JSch jsch = new JSch();
        Session session;

        try {
            Log.w("ImageSenderThread", "Trying to run server script");
            session = jsch.getSession(userName, this.serverAddress, serverPort);

            session.setConfig(this.config);
            session.setPassword(this.password);
            session.connect();

            String command = "bash -s < " + this.path + this.serverScriptName;
            Log.w("ImageSenderThread", command);
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            channel.setInputStream(null);
            InputStream in = channel.getInputStream();

            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0)
                        break;
                    // System.out.print(new String(tmp, 0, i));

                }
                if (channel.isClosed()) {
                    // System.out.println("exit-status: "+channel.getExitStatus());
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    return -2;
                }
            }
            //Thread.sleep(1000);

            channel.disconnect();
            session.disconnect();
        } catch (JSchException e) {
            e.printStackTrace();
            return -2;
        } catch (Exception e) {
            e.printStackTrace();
            return -2;
        }
        return 0;
    }

    private String getResultFile() {
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(this.userName, this.serverAddress, this.serverPort);

            session.setConfig(this.config);
            session.setPassword(this.password);
            session.connect();

            String command = "scp -f " + this.path + "result";
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            //Send OK flag to server
            out.write(0);
            out.flush();

            byte[] buffer = new byte[1024];
            //Read file from server
            int c = checkAck(in);
            if (c != 'C') {
                Log.w("ImageSenderThread", "Error when reading ack resopnse mode flag");
                return null;
            }
            in.read(buffer, 0, 5);

            //Read filesize from package header
            long filesize = 0L;
            while (true) {
                if (in.read(buffer, 0, 1) < 0) {
                    // error while reading filesize
                    Log.w("ImageSenderThread", "Error when reading header field filesize");
                    return null;
                }
                if (buffer[0] == ' ') break;
                filesize = filesize * 10L + (long) (buffer[0] - '0');
            }

            //Read filename from package header
            for (int i = 0; ; i++) {
                in.read(buffer, i, 1);
                if (buffer[i] == (byte) 0x0a) {
                    break;
                }
            }

            out.write(0);
            out.flush();

            //Read file content
            int maxSize = 2048;
            int fileContentBufferSize = filesize > maxSize ? maxSize : (int) filesize;
            byte[] fileContentBuffer = new byte[fileContentBufferSize];

            int readBytes = 0;
            while (readBytes < fileContentBufferSize) {
                readBytes += in.read(fileContentBuffer, readBytes, fileContentBufferSize - readBytes);
            }

            if (readBytes < 0) {
                // error no data could be recieved
                Log.w("ImageSenderThread", "Content of result file was not correctly recieved");
                return null;
            }

            out.write(0);
            out.flush();

            closeConnection(session, channel, in, out);

            return new String(fileContentBuffer);
        } catch (JSchException e) {
            e.printStackTrace();
            handleOnNoConnection();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            handleOnNoConnection();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    static int checkAck(InputStream in) throws IOException {
        int b = in.read();
        Log.w("ImageSenderThread", "" + b);
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if (b == 0) return b;
        if (b == -1) return b;

        if (b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
                c = in.read();
                sb.append((char) c);
            }
            while (c != '\n');
            if (b == 1) { // error
                Log.w("ImageSenderThread", sb.toString());
            }
            if (b == 2) { // fatal error
                Log.w("ImageSenderThread", sb.toString());
            }
        }
        return b;
    }

    /**
     * Adds an image to the send queue.
     */
    public void sendImage(byte[] imgBytes) {
        queue.add(imgBytes);
    }

    /**
     * Close this thread and return a ServerResponse
     *
     * @return ServerResponse based on the images sent
     */
    public void close() {
        open = false;
    }

    /**
     * Get the server response
     *
     * @return Response as a string else null if the response has not yet been received
     */
    public String getServerResponse() {
        return response;
    }

    /**
     * Turn an int into an array of 4 bytes.
     *
     * @param value Integer value
     * @return int as array of 4 bytes
     */
    private byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    public boolean addResponseListener(ResponseCallback callback) {
        if (!responseListeners.contains(callback)) {
            responseListeners.add(callback);
            return true;
        }
        return false;
    }

    public boolean removeResponseListener(ResponseCallback callback) {
        return responseListeners.remove(callback);
    }

    private void handleOnResponse(final String response) {
        for (final ResponseCallback o : responseListeners) {
            o.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    o.onResponse(response);
                }
            });
        }
    }

    private void handleOnNoConnection() {
        for (final ResponseCallback o : responseListeners) {
            o.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    o.onNoConnection();
                }
            });
        }
    }

    private class MyUserInfo implements UserInfo {

        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String s) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String s) {
            return false;
        }

        @Override
        public boolean promptYesNo(String s) {
            return false;
        }

        @Override
        public void showMessage(String s) {

        }
    }
}