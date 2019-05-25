package se.nugify.frixumpullum.app.backend;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.text.TextUtilsCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by tomas on 2/25/17.
 * (Change later maybe?) :3
 *
 * Known error Crashes after maybe 60 seconds on emulator?
 *
 *
 * To send over network ParcelFileDescriptor should be used.
 *
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP) //<-- This line
public class CameraController {


    // Singleton this class  should NEVER have more then one instance
    public static CameraController GLOBAL_CAMERA_MANAGER;

    // The underlying camera manager object
    private CameraManager androidCameraManager;
    private CameraCharacteristics characteristics;
    private CameraDevice camera;
    private TextureView preview;
    private Context androidContext;
    private Size imageDimensions;

    private String cameraID;
    private CameraCaptureSession cameraCaptureSessions;

    private HandlerThread backgroundThread;
    private HandlerThread imageReaderThread;
    private Handler backgroundThreadHandler;
    private Handler imageReaderThreadHandler;

    private CaptureRequest.Builder captureRequestBuilder;

    // An array containing all observeYMuf0cMop@nXrs for different camera status changes.
    private ArrayList<CameraStatusListener> cameraStatusListeners = new ArrayList<>();

    // An array containing all observers for image updates
    private ArrayList<ImageListener> imageListeners = new ArrayList<>();

    // The image format that the camera should capture images in see ImageFormat class
    private int selectedImageFormat = 0;

    // Seems to allways be the back camera.  Might be wrong though
    private final static int DEFAULT_CAMERA = 0;

    // The tag used in the camera managers debug output
    private final static String LOGTAG = "Camera manager";

    // For a cleaner interface to names and preferred formats
    private final static List<Integer> PREFERRED_FORMATS = Arrays.asList(new Integer[]{ ImageFormat.YUV_420_888});
    private final static Map<Integer,String> IMAGE_FORMAT_NAMES = new HashMap<>();

    // As requested by calin
    private final static Size PREFERRED_SIZE = new Size(640, 480);
    static{
        IMAGE_FORMAT_NAMES.put(ImageFormat.JPEG, "JPEG");
        IMAGE_FORMAT_NAMES.put(ImageFormat.YUV_420_888, "YUV_420_888");
    }

    /**
     * Constructor
     * @param view
     * @param c
     */
    private CameraController(TextureView view, Context c){
        preview = view;
        androidContext = c;
        androidCameraManager = (CameraManager)androidContext.getSystemService(Context.CAMERA_SERVICE);
        startBackgroundThread();
    }


    /**
     * The factory method for the camera manager. The camera manager should be a singleton.
     * @param preview
     * @param context
     * @return
     */
    public static CameraController getManager(TextureView preview, Context context){
        if(GLOBAL_CAMERA_MANAGER == null){
            GLOBAL_CAMERA_MANAGER = new CameraController(preview, context);
        }
        return GLOBAL_CAMERA_MANAGER;
    }

    /**
     * For retrieving the selected image format without.
     *
     * @return The prefered image format.
     */
    private int getSelectedImageFormat(){
        if(selectedImageFormat == 0){
            Log.d(LOGTAG, "Has not retrieved the image format yet");
        }
        return selectedImageFormat;
    }

    /**
     * Retrieves the locally preferred image format from the map if it hasn't already been selected.
     * @param map the 'android configuration map'
     * @return The preferred image format.
     */
    private int getPreferredImageFormat(StreamConfigurationMap map){
        Log.d(LOGTAG, "Available output formats" + Arrays.toString(map.getOutputFormats()));
        if(selectedImageFormat != 0)
            return selectedImageFormat;


        for( Integer prefered : PREFERRED_FORMATS){
            for( int available : map.getOutputFormats()){
                Log.d(LOGTAG, "Available format: " + IMAGE_FORMAT_NAMES.get(available));
                if (available == prefered) {
                    selectedImageFormat = prefered;
                    return selectedImageFormat;
                }
            }
        }

        // Since nothing has been found
        Log.d(LOGTAG, "No suitable image compression was found");
        return 0;
    }


    // ?, camera settings something something
    private CaptureRequest.Builder createCaptureRequestBuilder(List<Surface> surfaces){
        try {
            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            for(Surface surface : surfaces) {
                builder.addTarget(surface);
            }
            return builder;
        } catch(CameraAccessException e){
            e.printStackTrace();
            fuck("Cannot create a capture request");
            return null;
        }
    }

    /**
     * Prepare the preview surface
     * @returnf
     */
    private Surface createCameraPreviewSurface() {
        SurfaceTexture texture = preview.getSurfaceTexture();
        texture.setDefaultBufferSize(imageDimensions.getWidth(), imageDimensions.getHeight());

        return new Surface(texture);
    }

    private void transformImage(int width, int height) {

        Size mPreviewSize = imageDimensions;
        TextureView mTextureView = preview;
        Matrix matrix = new Matrix();
        int rotation = ((AppCompatActivity)androidContext).getWindowManager().getDefaultDisplay().getRotation();
        RectF textureRectF = new RectF(0, 0, width, height);
        RectF previewRectF = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = textureRectF.centerX();
        float centerY = textureRectF.centerY();
        if(rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            previewRectF.offset(centerX - previewRectF.centerX(),
                    centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float)width / mPreviewSize.getWidth(),
                    (float)height / mPreviewSize.getHeight());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }

        mTextureView.setTransform(matrix);
    }


    /**
     * Prepares the image reader, the function that will recieve each image.
     * @return
     */
    private ImageReader reader_reference; // keep this!!! Else it will be garbage collected and we do not want this!s
    private Surface image_reader_reference;

    private Surface prepareImageReader(){
        ImageReader reader = ImageReader.newInstance(imageDimensions.getWidth(),imageDimensions.getHeight(), getSelectedImageFormat(), 10);
        reader_reference = reader;
        reader.setOnImageAvailableListener(imageAvailableListener, imageReaderThreadHandler);
        image_reader_reference = reader.getSurface();
        return image_reader_reference;
    }


    private void addImageReader(){
        Log.e(LOGTAG, "Image reader: " + image_reader_reference.toString());
        captureRequestBuilder.addTarget(image_reader_reference);
        try{
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundThreadHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void removeImageReader(){
        captureRequestBuilder.removeTarget(image_reader_reference);
        try{
            Log.e(LOGTAG, captureRequestBuilder + "  " + backgroundThreadHandler);
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundThreadHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void initCaptureSession(List<Surface> surfaces){
        try {
            camera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    if(null == camera) {
                        Log.e(LOGTAG, "updatePreview error, return");
                    }
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    try {
                        cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundThreadHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(androidContext, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch(CameraAccessException e){
            e.printStackTrace();
            fuck("Could not create capture session");
        }
    }

    /**
     * Attempts to retrieve the prefered iamge size. If this isn't possible
     * It retieves the largest it
     * @param map
     * @return
     */
    private Size getImageDimentions(StreamConfigurationMap map){

        if(imageDimensions != null){
            return imageDimensions;
        }

        List<Size> outputSizes = Arrays.asList(map.getOutputSizes(SurfaceTexture.class));
        List<Size> highSpeedSizes = Arrays.asList(map.getHighSpeedVideoSizes());

        final float MAX_ERROR = 0.001f;
        if(outputSizes.isEmpty()){
            Log.e(LOGTAG, "Could not find any suitable surface textures");
            fuck("Everything");

        }
        if(outputSizes.contains(PREFERRED_SIZE)){
            imageDimensions = PREFERRED_SIZE;
            Log.d(LOGTAG, "Found the perefered image size " + PREFERRED_SIZE);
            return imageDimensions;
        }


        // If there are high speed sizes we reaaaaly want to chose one of them
        if(!highSpeedSizes.isEmpty()) {
            imageDimensions  = selectClosestMatching(highSpeedSizes, PREFERRED_SIZE, MAX_ERROR);
            Log.d(LOGTAG, "getImageDimentions: selected a high speed format." + imageDimensions);
        } else {
            imageDimensions = selectClosestMatching(outputSizes, PREFERRED_SIZE, MAX_ERROR);
            Log.d(LOGTAG, "seletcted slow size " + imageDimensions);
        }

        return imageDimensions;
    }

    /**
     * Util function for selecting file size
     * @param sizes
     * @param preferred
     * @param maxError
     * @return
     */
    public Size selectClosestMatching(List<Size> sizes, Size preferred, float maxError){
        final float preferredRatio = (float) preferred.getWidth() / (float) preferred.getHeight();
        final float maxRatio = preferredRatio + maxError;
        final float minRatio = preferredRatio - maxError;

        final Size REALLY_LARGE_SIZE = new Size(Integer.MAX_VALUE, Integer.MAX_VALUE);
        Size minSize = REALLY_LARGE_SIZE;
        Size altCorrectRatio = REALLY_LARGE_SIZE;
        Size minAlternative = REALLY_LARGE_SIZE;

        for (Size size : sizes) {
            float ratio = (float) size.getWidth() / (float) size.getHeight();
            boolean isLargerThenPreferred = size.getWidth() > preferred.getWidth();
            boolean isSmallerThenCurrentMin = minSize.getWidth() > size.getWidth();
            boolean hasCorrectRatio = maxRatio > ratio && ratio > minRatio;
            if (hasCorrectRatio){
                if(isLargerThenPreferred && isSmallerThenCurrentMin){
                    minSize = size;
                } else if(altCorrectRatio.getWidth() < size.getWidth()){
                    altCorrectRatio = size;
                }
            } else if(isLargerThenPreferred  && size.getWidth() < minAlternative.getWidth()){
                minAlternative = size;
            }
        }
        if(!minSize.equals(REALLY_LARGE_SIZE)){
            return minSize;
        }else if( !altCorrectRatio.equals(REALLY_LARGE_SIZE)){
            return altCorrectRatio;
        }else if( !minAlternative.equals(REALLY_LARGE_SIZE)){
            return minAlternative;
        }
        return null;
    }

    /**
     * Responsible for retrieving the camera device, actual capture done elsewhere.
     */
    private void openCamera(){
        Log.d(LOGTAG, "Opening camera");
        try {
            // Camera nr 0 seems to give us the back camera but this is not guaranteed
            cameraID = androidCameraManager.getCameraIdList()[DEFAULT_CAMERA];

            characteristics = androidCameraManager.getCameraCharacteristics(cameraID);

            //?
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            getPreferredImageFormat(map);
            getImageDimentions(map);

            prepareImageReader();

            // Checks if we have permisions to the camera, if not asks for them
            if (ActivityCompat.checkSelfPermission(androidContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions((Activity)androidContext, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 200);
                // Controlled crash if answer says no
                return;
            }

            CameraDevice.StateCallback callback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull  CameraDevice cameraDevice) {
                    //This is called when the camera is open
                    Log.e(LOGTAG, "onOpened");
                    camera = cameraDevice;

                    ArrayList<Surface> surfaces = new ArrayList<>(2);
                    surfaces.add(createCameraPreviewSurface());
                    surfaces.add(image_reader_reference);
                    captureRequestBuilder = createCaptureRequestBuilder(surfaces);
                    captureRequestBuilder.removeTarget(image_reader_reference);
                    initCaptureSession(surfaces);

                }
                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    Log.e(LOGTAG, "onDisconnected");
                    cameraDevice.close();
                }
                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int error) {
                    Log.e(LOGTAG, "onError returned error: " + error);
                    cameraDevice.close();
                    camera = null;
                }
            };
            androidCameraManager.openCamera(cameraID, callback, backgroundThreadHandler);

        } catch (CameraAccessException e){
            Log.e(LOGTAG, "Camera open failed");
            e.printStackTrace();
        }
        Log.d(LOGTAG, "Camera successfully acquired.");
    }

    // Skrivet av Carl inspo från "https://www.nigeapptuts.com/android-camera2-api-background-handler/"
    public void closeCamera(){
        if(cameraCaptureSessions != null){
            cameraCaptureSessions.close();
            cameraCaptureSessions = null;
        }
        if(camera != null){
            camera.close();
            camera = null;
        }
    }

    public void resume(){
        Log.d(LOGTAG, "Ran resume");
        startBackgroundThread();
        if(preview.isAvailable()){
            openCamera();
        }else{
            preview.setSurfaceTextureListener(previewListener);
        }
    }

    public void pause(){
        closeCamera(); // rad skriven av Carl
        Log.d(LOGTAG, "onPause");
        stopBackgroundThread();

    }

    private void startBackgroundThread() {
        Log.d(LOGTAG, "Starting background thread");
        backgroundThread = new HandlerThread("Camera Background");
        backgroundThread.start();
        backgroundThreadHandler = new Handler(backgroundThread.getLooper());

        imageReaderThread = new HandlerThread("Image reader thread");
        imageReaderThread.start();
        imageReaderThreadHandler = new Handler(imageReaderThread.getLooper());

    }
    private void stopBackgroundThread() {
        Log.d(LOGTAG, "Stopping background thread");
        backgroundThread.quitSafely();
        imageReaderThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundThreadHandler = null;

            imageReaderThread.join();
            imageReaderThread = null;
            imageReaderThreadHandler = null;
            Log.d(LOGTAG, "Background thread stopped");
        } catch (InterruptedException e) {
            Log.d(LOGTAG, "Failed to stop background thread");
            e.printStackTrace();
        }
    }

    /**
     * Callback handler object for the surface. Mainly necesarry for the openCamera method.
     */
    private TextureView.SurfaceTextureListener previewListener = new TextureView.SurfaceTextureListener(){
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();

        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.d(LOGTAG, "Surface texture size changed");
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.d(LOGTAG, "Surface destroyed");
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            Log.d(LOGTAG, "Surface texture updated");
            handleOnCameraOpen();
            preview.setSurfaceTextureListener(null);
        }
    };

    /**
     * This object contains the method for updating all the listeners.
     * A possible danger is to call close on the image anywhere else.
     * Make sure to not call close anywhere else. Otherwise other listners may fail.
     */
    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(LOGTAG, "Got an image");
            Image img = reader.acquireNextImage();

            if(img != null) {
                for (ImageListener listener : imageListeners) {
                    listener.onImageAvailable(img);
                }
                img.close();
            }
        }
    };

    /**
     * registers an image listener within the camera manager.
     * If the listeners previously was empty the camera recording is started.
     * @param listener
     */
    public void registerImageListener(ImageListener listener){
        if(imageListeners.size() == 0){
            addImageReader();
        }
        imageListeners.add(listener);
    }

    /**
     * Removes  a specific image listener from the set of listeners.
     * If this is the last listener this will stop all image capture.
     * @param listener
     * @return
     */
    public boolean removeImageListener(ImageListener listener){
        boolean removed = imageListeners.remove(listener);
        if(removed && imageListeners.size() == 0){
            removeImageReader();
        }
        return removed;
    }

    /**
     * The observer class for listening on image recording.
     */
    public static class ImageListener{
        public void onImageAvailable(Image img){}
    }
    /**
     * The camera callback class. Overide this and send it to the manager when you want to
     */
    public static class CameraStatusListener{
        private Handler handler;
        public CameraStatusListener(){
            handler = new Handler();
        }
        public void onCameraOpen(){}
        public void onCameraClose(){}
        public void onCameraRecording(){}
        public void onCameraStopRecording(){}
    }

    /**
     * Adds a status listener to to the camera manager
     * @param callback
     * @return
     */
    public boolean addStatusListener(CameraStatusListener callback){
        if(cameraStatusListeners.contains(callback))
            return false;
        else {
            cameraStatusListeners.add(callback);
            return true;
        }
    }

    /**
     * Removes a specific status listener for the camera manager.
     * @param callback
     * @return
     */
    public boolean removeStatusListener(CameraStatusListener callback){
        return cameraStatusListeners.remove(callback);
    }

    /**
     * Internal method for handeling all the events when the camera is opened
     */
    private void handleOnCameraOpen(){
        for( final CameraStatusListener o : cameraStatusListeners) {
            o.handler.post(new Runnable() {
                @Override
                public void run() {
                    transformImage(preview.getWidth(),preview.getHeight());
                    o.onCameraOpen();
                }
            });
        }
    }

    /**
     * Internal method for triggering all the onCameraOpen events
     *
     */
    private void handleOnCameraClose(){
        for( CameraStatusListener o : cameraStatusListeners) {
            o.onCameraOpen();
        }
    }

    /**
     * Internal method for triggering  all the onRecording events
     */
    private void handleCameraRecording(){
        for( CameraStatusListener o : cameraStatusListeners) {
            o.onCameraRecording();
        }
    }
    /**
     * Internal method to handle all the events for when the camera stops recording
     */
    private void handleCameraStopRecording(){
        for( CameraStatusListener o : cameraStatusListeners) {
            o.onCameraStopRecording();
        }
    }

    /**
     * Debug method for epic crashes. Should be removed in final version.
     * @param msg
     */
    public void fuck(String msg){
        Log.d(LOGTAG, "I killed myself " + msg);
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }

}
