package se.nugify.frixumpullum.app.backend;

import android.os.Handler;
import android.util.Log;

import se.nugify.frixumpullum.app.backend.util.ResponseCallback;


/** TextProcessor handles data given from the ImageProcessor.
 *
 * @version 0
 * @author Marcus Östling, Gustav Nelson Schneider
 */

public class TextProcessor {
    private String currentResponse = "Potato";
    private ImageProcessor imageProcessor;
    /**
     * Constructor for TextProcessor
     *
     * @param imageProcessor ImageProcessor
     */
    public TextProcessor(ImageProcessor imageProcessor, ResponseCallback callback) {
        this.imageProcessor = imageProcessor;
        imageProcessor.setResponseCallback(callback);
    }

    /** Tell imageProcessor to start recording
     */
    public void startTextPoll(){
        imageProcessor.startRecording();
    }

    /** Tell imageProcessor to stop recording
     */
    public void stopTextPoll(){
        imageProcessor.stopRecording();
    }

    /** Process string given by imageprocessor
     *
     * @return Processed string
     */
    private String processText() {
        //currentResponse = imageProcessor.getServerResponse();
        //process process
        return currentResponse;
    }

    /** Get the word translated from the camera
     *
     * @return word
     */
    public String getWord(){
        stopTextPoll();
        return processText();
    }


}
