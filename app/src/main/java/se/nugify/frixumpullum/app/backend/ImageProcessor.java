package se.nugify.frixumpullum.app.backend;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import se.nugify.frixumpullum.app.backend.util.ResponseCallback;

/** ImageProcessor listens for images from CameraController and sends them to
 *  a server when startRecording is called.
 *
 *  @author Marcus Östling, Gustav Nelson Schneider
 */
public class ImageProcessor {

    private CameraController cameraManager;

    //Debug
    private static final String DEBUG = "ImageProcessor";

    //Server fields
    private static final int serverPort = 22;
    private static final String serverAddress = "130.237.67.222";

    private ImageSenderThread activeThread = null;

    private CameraController.ImageListener listener;
    private ResponseCallback callback;

    /** ImageProcessor constructor, sets up and connect to the server.
     *
     * @param cameraManager
     */
    public ImageProcessor(CameraController cameraManager) {
        this.cameraManager = cameraManager;
    }

    /** Starts sending images to the ImageSenderThread
     */
    public void startRecording(){
        if(activeThread != null) {
            Log.d(DEBUG, "Fatal: Already have an active recording!");
        }
        Log.w(DEBUG, "New senderThread");

        activeThread = new ImageSenderThread(serverAddress, serverPort);
        activeThread.addResponseListener(callback);
        activeThread.addResponseListener(new ResponseCallback() {
           @Override
            public void onNoConnection() {
               stopRecording();
           }
        });

        activeThread.start();
        listener = new CameraController.ImageListener(){
            @Override
            public void onImageAvailable(Image img){
                Log.w(DEBUG, "New img");
                switch(img.getFormat()) {
                    case 35: // YUV_420_888
                        Log.w(DEBUG, "YUV_420_888");
                        activeThread.sendImage(scaleJpeg(yuvTojpegBytes(img),640,480));
                        break;
                    case 256: // JPEG
                        Log.w(DEBUG, "JPEG");
                        activeThread.sendImage(scaleJpeg(jpegToBytes(img),640,480));
                        break;
                }
            }

        };
        cameraManager.registerImageListener(listener);
    }

    /** Stops sending images to the ImageSenderThread
     *
     * @return ServerResponse based on the images sent
     */
    public void stopRecording(){
        cameraManager.removeImageListener(listener);
        activeThread.close();
    }

    /** Get the server response
     *
     * @return Response as a string else null if the response has not yet been received
     */
    public String getServerResponse() {
        return activeThread.getServerResponse();
    }

    /** Convert yuv to jpeg byte array
     *
     * @param img yuv image
     * @return jpeg byte array
     */
    private byte[] yuvTojpegBytes(Image img) {

        // Too NV21
        Image.Plane Y = img.getPlanes()[0];
        Image.Plane U = img.getPlanes()[1];
        Image.Plane V = img.getPlanes()[2];

        int Yb = Y.getBuffer().remaining();
        int Ub = U.getBuffer().remaining();
        int Vb = V.getBuffer().remaining();

        byte[] data = new byte[Yb + Ub + Vb];

        Y.getBuffer().get(data, 0, Yb);
        U.getBuffer().get(data, Yb, Ub);
        V.getBuffer().get(data, Yb + Ub, Vb);

        // From NV21 to jpeg
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(data, ImageFormat.NV21, img.getWidth(), img.getHeight(), null);
        yuv.compressToJpeg(new Rect(0, 0, img.getWidth(), img.getHeight()), 100, out);
        return out.toByteArray();
    }

    /** Convert a JPEG image to an array of bytes
     *
     * @param img Jpeg image
     * @return Byte array
     */
    private byte[] jpegToBytes(Image img) {
        ByteBuffer bb = img.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        return bytes;
    }

    /** Scale a jpeg byte array
     *
     * @param jpegBytes Byte array to scale
     * @param width new width
     * @param height new height
     * @return scaled byte array
     */
    private byte[] scaleJpeg(byte[] jpegBytes, int width, int height) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jpegBytes);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);

        scaled.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

        return outputStream.toByteArray();
    }

    public void setResponseCallback(ResponseCallback callback) {
        this.callback = callback;
    }


}
