package se.nugify.frixumpullum.app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.ImageButton;

import se.nugify.frixumpullum.app.backend.ImageProcessor;
import se.nugify.frixumpullum.app.backend.CameraController;
import se.nugify.frixumpullum.app.backend.TextProcessor;
import se.nugify.frixumpullum.app.backend.util.ResponseCallback;
import se.nugify.frixumpullum.frixumpullum.R;

public class MainActivity extends AppCompatActivity {
    
    TextView textView;
    View recButton;
    ImageButton recBorder;
    TextureView preview;
    View textBox;

    CameraController cameraManager;
    ImageProcessor imageProcessor;
    TextProcessor textProcessor;

    GradientDrawable gradientDrawable;
    AnimatorSet animatorSet;
    ObjectAnimator toRectangle;
    ObjectAnimator toCircle;

    int animationDuration;
    boolean recording;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        textView = (TextView) findViewById(R.id.textView);
        recButton = findViewById(R.id.recButton);
        recButton.setVisibility(View.GONE);
        recBorder = (ImageButton) findViewById(R.id.recordBorder);
        recBorder.setVisibility(View.GONE);
        preview = (TextureView) findViewById(R.id.textureView);
        textBox = findViewById(R.id.boxView);
        preview = (TextureView) findViewById(R.id.textureView);

        animatorSet = new AnimatorSet();
        recording = false;
        animationDuration =  getResources().getInteger(android.R.integer.config_shortAnimTime);

        gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(500.0f);
        gradientDrawable.setShape(GradientDrawable.RECTANGLE);
        gradientDrawable.setColor(Color.RED);
        gradientDrawable.setVisible(true, true);

        recButton.setBackground(gradientDrawable);

        cameraManager = CameraController.getManager(preview, this);
        cameraManager.addStatusListener(new CameraController.CameraStatusListener(){
            @Override
            public void onCameraOpen(){
                recButton.setVisibility(View.VISIBLE);
                recBorder.setVisibility(View.VISIBLE);
            }
        });

        imageProcessor = new ImageProcessor(cameraManager);
        textProcessor  = new TextProcessor(imageProcessor, new ResponseCallback() {
            @Override
            public void onResponse(String response) {
                textView.setText(response);
                textView.setVisibility(View.VISIBLE);
                stopRecording();
                textView.startAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.fadein));
            }

            @Override
            public void onNoConnection() {
                //TODO Show something when there is no connection
                Log.d("MainActivity", "onNoConnection");
                textView.setText("NO CONNECTION TO SERVER");
                textView.setVisibility(View.VISIBLE);
                stopRecording();
                textView.startAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.fadein));
            }
        });

        //hide textbox until we get result from server
        textView.setVisibility(View.GONE);

        recBorder.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                if (!recording && !animatorSet.isRunning()) {
                    startRecording();

                } else if (!animatorSet.isRunning()) {
                    stopRecording();
                }

            }
        });
    }

    void textFade() {
        Animation fadeOut = new AlphaAnimation(0.7f, 0.0f);
        fadeOut.setInterpolator(new AccelerateInterpolator());
        fadeOut.setDuration(100);

        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            public void onAnimationEnd(Animation animation)
            {
                textView.setVisibility(View.GONE);
            }
            public void onAnimationRepeat(Animation animation) {}
            public void onAnimationStart(Animation animation) {}
        });
        textView.startAnimation(fadeOut);
        textBox.startAnimation(fadeOut);
    }


    private void startRecording(){
        textProcessor.startTextPoll();
        textFade();
        recording = true;

        recButton.animate().scaleX(0.7f).scaleY(0.7f).setDuration(animationDuration).setListener(null);

        toRectangle = ObjectAnimator.ofFloat(gradientDrawable, "cornerRadius", 90.0f, 30.0f);

        animatorSet.setDuration(200);
        animatorSet.play(toRectangle);
        animatorSet.start();
    }

    private void stopRecording(){
        textProcessor.stopTextPoll();

        recording = false;

        recButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(animationDuration).setListener(null);

        toCircle = ObjectAnimator.ofFloat(gradientDrawable, "cornerRadius", 30.0f, 500.0f);

        animatorSet.setDuration(500);
        animatorSet.play(toCircle);
        animatorSet.start();
    }

    @Override
    protected void onResume() {
        Log.e("TEST", "main onPause");
        super.onResume();
        cameraManager.resume();
    }

    @Override
    protected void onPause() {
        Log.e("TEST", "main onPause");
        cameraManager.pause();
        super.onPause();
    }
}
