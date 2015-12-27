package haagch.osvrsensorservice;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    static final String TAG = "OSVRSensorService";
    static final double DEG = 180 / Math.PI;

    SensorManager sensorManager;
    float[] gData = new float[3];           // Gravity or accelerometer
    float[] mData = new float[3];           // Magnetometer
    float[] orientation = new float[3];
    float[] Rmat = new float[9];
    float[] quat = new float[4];
    float[] R2 = new float[9];
    float[] Imat = new float[9];
    boolean haveGrav = false;
    boolean haveAccel = false;
    boolean haveMag = false;


    BlockingQueue<float[]> sensordataToSend = new ArrayBlockingQueue<>(50); // should be enough
    public void onSensorChanged(SensorEvent event) {
        switch( event.sensor.getType() ) {
            case Sensor.TYPE_GRAVITY:
                gData[0] = event.values[0];
                gData[1] = event.values[1];
                gData[2] = event.values[2];
                haveGrav = true;
                break;
            case Sensor.TYPE_ACCELEROMETER:
                if (haveGrav) break;    // don't need it, we have better
                gData[0] = event.values[0];
                gData[1] = event.values[1];
                gData[2] = event.values[2];
                haveAccel = true;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mData[0] = event.values[0];
                mData[1] = event.values[1];
                mData[2] = event.values[2];
                haveMag = true;
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                SensorManager.getQuaternionFromVector(quat,
                        event.values);
                //Log.i(TAG, "Rotationvector: " + Arrays.toString(quat));
                break;

            default:
                return;
        }

        if (true/*(haveGrav || haveAccel) && haveMag*/) {
            //SensorManager.getRotationMatrix(Rmat, Imat, gData, mData);
            //SensorManager.remapCoordinateSystem(Rmat,
            //        SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, R2);
            if (sensordataToSend.remainingCapacity() > 0) {
                //float[] quat = new float[4];
                //SensorManager.getQuaternionFromVector(quat, Rmat);
                sensordataToSend.add(quat);
            }
            // Orientation isn't as useful as a rotation matrix, but
            // we'll show it here anyway.
            //SensorManager.getOrientation(R2, orientation);
            //float incl = SensorManager.getInclination(Imat);
            //Log.v(TAG, "mh: " + (int) (orientation[0] * DEG));
            //Log.v(TAG, "pitch: " + (int)(orientation[1]*DEG));
            //Log.v(TAG, "roll: " + (int)(orientation[2]*DEG));
            //Log.v(TAG, "yaw: " + (int)(orientation[0]*DEG));
            //Log.v(TAG, "inclination: " + (int)(incl*DEG));
        } else {
            //Log.i(TAG, "Grav: " + haveGrav + " Accel: " + haveAccel + " Mag: " + haveMag);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // don't care
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sensorManager =
                (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    //http://stackoverflow.com/a/15293227
    public static String strJoin(float[] aArr, String sSep) {
        StringBuilder sbStr = new StringBuilder();
        for (int i = 0, il = aArr.length; i < il; i++) {
            if (i > 0)
                sbStr.append(sSep);
            sbStr.append(String.valueOf(aArr[i]));
        }
        return sbStr.toString();
    }

    class ClientTask implements Runnable {
        Socket s;

        ClientTask(Socket s) {
            this.s = s;
        }

        @Override
        public void run() {
            Log.i(TAG, "Start Sending to client " + s.toString());
            try {
                PrintWriter out = new PrintWriter(s.getOutputStream());
                while (true) {
                    float[] q = sensordataToSend.take();
                    String s = strJoin(q, ",");

                    out.write(s + "\n");
                    out.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                try {
                    s.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Sensor gsensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        //Sensor asensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        //Sensor msensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        Sensor rotsensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        //sensorManager.registerListener(this, gsensor, SensorManager.SENSOR_DELAY_GAME);
        //sensorManager.registerListener(this, asensor, SensorManager.SENSOR_DELAY_GAME);
        //sensorManager.registerListener(this, msensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, rotsensor, SensorManager.SENSOR_DELAY_GAME);
        Runnable serverTask = new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(8000);
                    System.out.println("Waiting for clients to connect...");
                    while (true) {
                        Socket clientSocket = serverSocket.accept();
                        Log.i(TAG, "Accepted client " + clientSocket.toString());
                        new Thread(new ClientTask(clientSocket)).start();
                    }
                } catch (IOException e) {
                    System.err.println("Unable to process client request: " + e.getMessage());
                    //e.printStackTrace();
                }
            }
        };
        Thread serverThread = new Thread(serverTask);
        serverThread.start();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
