package ua.dp.eroy.machineblick;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.ValueBar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    private static final String TAG = "bluetooth1";

    // SPP UUID сервиса
    private static String macBluetooth = "";
    private final int RECIEVE_MESSAGE = 1;
    private BluetoothDevice device;

    private Handler h;
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private OutputStream outStream = null;

    private ArrayList<String> arrayListMessage;
    private ArrayAdapter<String> arrayAdapter;

    private ProgressDialog pd;

    private StringBuilder sb = new StringBuilder();

    private ConnectedThread mConnectedThread;

    private CoordinatorLayout coordinatorLayout;

    private SharedPreferences sharedPreferences;

    /*
        new values
    */
    private RelativeLayout rlTime;
    private RelativeLayout rlAlarm;
    private RelativeLayout rlLaser;
    private RelativeLayout rlLight;
    private RelativeLayout rlManual;

    private Toolbar toolbar;
    private ListView listView;

    private Switch laser;
    private CheckBox oneLaser;
    private CheckBox secondLaser;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rlTime = (RelativeLayout) findViewById(R.id.rlTime);
        rlAlarm = (RelativeLayout) findViewById(R.id.rlAlarm);
        rlLaser = (RelativeLayout) findViewById(R.id.rlLaser);
        rlLight = (RelativeLayout) findViewById(R.id.rlLight);
        rlManual = (RelativeLayout) findViewById(R.id.rlManual);

        listView = (ListView) findViewById(R.id.listView);

        initPD();
        arrayListMessage = new ArrayList<>();
        isEnableButton(false);
        searchAndConnectBT(Const.REQUEST_CONNECT_DEVICE_SECURE);

        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinatorLayout);

        toolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);

        setSupportActionBar(toolbar);
        toolbar.setOverflowIcon(getResources().getDrawable(R.drawable.ic_more_vert_white_24dp));


        rlTime.setOnClickListener(this);
        rlAlarm.setOnClickListener(this);
        rlLaser.setOnClickListener(this);
        rlLight.setOnClickListener(this);
        rlManual.setOnClickListener(this);


        toolbar.setSubtitle(R.string.not_connect);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();


        h = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECIEVE_MESSAGE:                                                   // если приняли сообщение в Handler
                        byte[] readBuf = (byte[]) msg.obj;
                        String strIncom = new String(readBuf, 0, msg.arg1);
                        sb.append(strIncom);                                                // формируем строку
                        int endOfLineIndex = sb.indexOf("\r\n");                            // определяем символы конца строки
                        if (endOfLineIndex > 0) {                                            // если встречаем конец строки,
                            String sbprint = sb.substring(0, endOfLineIndex);               // то извлекаем строку
                            sb.delete(0, sb.length());                                      // и очищаем sb

                            Snackbar snackbar = Snackbar.make(coordinatorLayout, sbprint, Snackbar.LENGTH_LONG);
                            snackbar.getView().setBackgroundColor(Color.WHITE);
                            snackbar.show();

                            arrayListMessage.add(sbprint);
                            arrayAdapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.message, arrayListMessage);
                            listView.setAdapter(arrayAdapter);
                            Log.d("SEND_BT", sbprint);

                        }
                        //Log.d(TAG, "...Строка:"+ sb.toString() +  "Байт:" + msg.arg1 + "...");
                        break;
                }
            }


        };



    }

    private void isEnableButton(boolean enable) {
        rlTime.setEnabled(enable);
        rlAlarm.setEnabled(enable);
        rlLaser.setEnabled(enable);
        rlLight.setEnabled(enable);
        rlManual.setEnabled(enable);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.rlTime:
                setTimeAndAlertDialog("Установка времени", Const.CLOCK_HOUR, Const.CLOCK_MINUTES, false);
                break;
            case R.id.rlAlarm:
                setTimeAndAlertDialog("Установка будульника", Const.ALARM_HOUR, Const.ALARM_MINUTES, true);
                break;
            case R.id.rlLaser:
                setLaserDialog("Установка лазера");

                break;
            case R.id.rlLight:
                setLightDialog("Установка цвета");

                break;
            case R.id.rlManual:
                setManualDialog("Ручной ввод комманд");
                break;

        }


    }

    private void initPD() {
        pd = new ProgressDialog(this);
        pd.setMessage("Соединение...");
        pd.setCancelable(false);
    }

    // dialog manual
    private void setManualDialog(String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        View view = getLayoutInflater().inflate(R.layout.set_manual_dialog, null);
        final EditText etMessage = (EditText) view.findViewById(R.id.etMsg);
        Button btnSet = (Button) view.findViewById(R.id.btnSend);
        final CheckBox chBigLetter = (CheckBox) view.findViewById(R.id.chBigLetter);
        chBigLetter.setChecked(true);

        builder.setView(view);
        final AlertDialog alertDialog = builder.create();
        btnSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = etMessage.getText().toString();
                if (chBigLetter.isChecked()) {
                    message = message.toUpperCase();
                }

                mConnectedThread.write(message);


                alertDialog.dismiss();
            }
        });


        alertDialog.show();

    }


    //dialog light
    private void setLightDialog(String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogFullScreen);
        builder.setTitle(title);

        View view = getLayoutInflater().inflate(R.layout.set_light_dialog, null);
        Button btnSet = (Button) view.findViewById(R.id.btnSetColor);
        final TextView textView = (TextView) view.findViewById(R.id.color);
        final ColorPicker picker = (ColorPicker) view.findViewById(R.id.picker);
        ValueBar valueBar = (ValueBar) view.findViewById(R.id.valuebar);
        picker.addValueBar(valueBar);

        textView.setText("#" + (Integer.toHexString(picker.getOldCenterColor())).substring(2, 8).toUpperCase());

        picker.setOnColorChangedListener(new ColorPicker.OnColorChangedListener() {
            @Override
            public void onColorChanged(int color) {
                String col = (Integer.toHexString(color)).substring(2, 8).toUpperCase();
                textView.setText("#" + col);
            }
        });

        builder.setView(view);
        final AlertDialog alertDialog = builder.create();
        btnSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String col = (Integer.toHexString(picker.getColor())).substring(2, 8).toUpperCase();

                picker.setOldCenterColor(picker.getColor());
//                textView.setText("#" + col);
                mConnectedThread.write(Const.LIGHT + col);
                alertDialog.dismiss();
            }
        });


        alertDialog.show();

    }


    //    dialog laser
    private void setLaserDialog(String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogFullScreen);
        builder.setTitle(title);

        View view = getLayoutInflater().inflate(R.layout.set_laser_dialog, null);
        laser = (Switch) view.findViewById(R.id.setLaser);
        oneLaser = (CheckBox) view.findViewById(R.id.setOneLaser);
        secondLaser = (CheckBox) view.findViewById(R.id.setSecondLaser);

        loadPreference();



        builder.setView(view);

        laser.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    if (oneLaser.isChecked() && secondLaser.isChecked()) {
                        mConnectedThread.write(Const.LASER_ON);
                    } else if (oneLaser.isChecked() && !secondLaser.isChecked()) {
                        mConnectedThread.write(Const.LASER_ONE);
                    } else if (!oneLaser.isChecked() && secondLaser.isChecked()) {
                        mConnectedThread.write(Const.LASER_TWO);
                    }

                } else {
                    mConnectedThread.write(Const.LASER_OFF);
                }
            }
        });


        final AlertDialog alertDialog = builder.create();

        alertDialog.show();

    }

    //    установка диалога
    private void setTimeAndAlertDialog(String title, final String constHours, final String constMinutes, Boolean isAlert) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogFullScreen);
        builder.setTitle(title);
        View view = getLayoutInflater().inflate(R.layout.set_time_dialog, null);
        RelativeLayout rlSettings = (RelativeLayout) view.findViewById(R.id.rlSettings);
        RelativeLayout rlSync = (RelativeLayout) view.findViewById(R.id.rlSync);
        TextView text2 = (TextView) view.findViewById(R.id.tvText2);


        builder.setView(view);
        final AlertDialog alertDialog = builder.create();
        if (isAlert) {
            text2.setText(R.string.alert_off);

            rlSync.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mConnectedThread.write(Const.ALARM_OFF);
                    alertDialog.dismiss();
                }
            });

        } else {
            rlSync.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Date date = new Date();
//                get hours and minutes now
                    int hours = Integer.parseInt(String.valueOf(date.getHours()));
                    int minutes = Integer.parseInt(String.valueOf(date.getMinutes()));
                    mConnectedThread.write(constHours + isSetZero(hours));

//                sleep between command
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    mConnectedThread.write(constMinutes + isSetZero(minutes));

                    alertDialog.dismiss();
                }
            });
        }

        rlSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Date date = new Date();
//                get hours and minutes now
                int hours = Integer.parseInt(String.valueOf(date.getHours()));
                final int minutes = Integer.parseInt(String.valueOf(date.getMinutes()));
                TimePickerDialog timePickerDialog = new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int timeHours, int timeMinutes) {
                        mConnectedThread.write(constHours + isSetZero(timeHours));

//                sleep between command
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        mConnectedThread.write(constMinutes + isSetZero(timeMinutes));

                        Toast.makeText(getApplicationContext(), timeHours + ":" + timeMinutes, Toast.LENGTH_LONG).show();
                        alertDialog.dismiss();
                    }
                }, hours, minutes, true);
                timePickerDialog.show();


            }
        });


        alertDialog.show();

    }


    //    проверка на ноль перед числом
    private String isSetZero(int number) {
        if (number < 10) {
            return "0" + number;
        } else {
            return String.valueOf(number);
        }
    }

    //    Асиннхронное подключение к устройству
    private void asyncConnectToDevice(final String adr) {
        AsyncTask<Void, Void, Void> remoteItem = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                pd.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                // Add some delay to the refresh/remove action.

                connectDevice(adr);

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                pd.dismiss();

//                if (btAdapter.getRemoteDevice(adr).getName().equals(btAdapter.getName())) {
//                    toolbar.setSubtitle(R.string.not_connect);
//                    isEnableButton(false);
//                } else {
                if (btSocket.isConnected()) {
                    toolbar.setSubtitle("Соединение установлено с: \n" + btAdapter.getRemoteDevice(adr).getName() + " (" + adr + ")");

                    toolbar.setSubtitleTextColor(getResources().getColor(R.color.colorAccent));

                    isEnableButton(true);
                } else {
                    if (adr.equals("00:00:00:00:00:00")) {
                        toolbar.setSubtitle(R.string.read_mode);
                        isEnableButton(true);
                    } else {
                        toolbar.setSubtitle(R.string.error_connect);
                        Snackbar.make(coordinatorLayout, R.string.error_connect, Snackbar.LENGTH_LONG).show();
                        isEnableButton(false);
                    }


                }

//                }
            }
        };
        remoteItem.execute();
    }

    //поиск и подключение к устройству
    private void connectDevice(String address) {

        device = btAdapter.getRemoteDevice(address);
        try {
            btSocket = device.createRfcommSocketToServiceRecord(Const.MY_UUID);
        } catch (IOException e) {
            showToast("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
            Log.d(TAG, "Fatal Error. In onResume() and socket create failed: " + e.getMessage());
        }

        btAdapter.cancelDiscovery();

        if (address.equals("00:00:00:00:00:00")) {
            try {
                btSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // Establish the connection.  This will block until it connects.
            Log.d(TAG, "...Соединяемся...");
            try {
                btSocket.connect();
                Log.d(TAG, "...Соединение установлено и готово к передачи данных...");
                showToast("Устройство подключено", "готово к передачи данных");
            } catch (IOException e) {
                try {
                    btSocket.close();
                    Log.d(TAG, "...Socket close...");
                } catch (IOException e2) {
                    Log.d(TAG, "Fatal Error, In onResume() and unable to close socket during connection failure" + e2.getMessage());

                    showToast("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
                }

            }
        }
        // Create a data stream so we can talk to server.
        Log.d(TAG, "...Создание Socket...");

        try {
            outStream = btSocket.getOutputStream();

        } catch (IOException e) {
            showToast("Fatal Error", "In onResume() and output stream creation failed:" + e.getMessage() + ".");
        }

        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeConnect();

        savePreference();

    }

    private void closeConnect() {
        if (outStream != null) {
            try {
                outStream.flush();

            } catch (IOException e) {
                Log.d("Fatal Error", "In onPause() and failed to flush output stream: " + e.getMessage() + ".");
            }
        }
        if (btSocket != null) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                Log.d("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
            }
        }
    }

    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        // Emulator doesn't support Bluetooth and will return null
        if (btAdapter == null) {
            Toast.makeText(getBaseContext(), "Fatal Error" + " - " + "Bluetooth не поддерживается", Toast.LENGTH_LONG).show();

        } else {
            if (btAdapter.isEnabled()) {
                Log.d(TAG, "...Bluetooth включен...");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(btAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, Const.REQUEST_ENABLE_BT);
            }
        }
    }


    private void showToast(final String title, final String message) {
        Handler handler = new Handler(Looper.getMainLooper());

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
            }
        }, 1000);


    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.secure_connect_scan:
                searchAndConnectBT(Const.REQUEST_CONNECT_DEVICE_SECURE);

                toolbar.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                return true;
            case R.id.insecure_connect_scan:
                searchAndConnectBT(Const.REQUEST_CONNECT_DEVICE_INSECURE);
                toolbar.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                return true;

            case R.id.read_mode:
                String mac = "00:00:00:00:00:00";
                asyncConnectToDevice(mac);
                toolbar.setBackgroundColor(getResources().getColor(R.color.colorAccent));
                return true;

            case R.id.closeConnect:
                closeConnect();
                toolbar.setSubtitle(R.string.not_connect);
                isEnableButton(false);

                toolbar.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                return true;
        }
        return false;
    }

    private void savePreference() {
        sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putBoolean("switchLaser", isCheckedSwitch(laser));
        editor.putBoolean("checkOneLaser", isCheckedBox(oneLaser));
        editor.putBoolean("checkSecondLaser", isCheckedBox(secondLaser));
        editor.apply();
    }

    private void loadPreference() {
        sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        laser.setChecked(sharedPreferences.getBoolean("switchLaser",false));
        oneLaser.setChecked(sharedPreferences.getBoolean("checkOneLaser",false));
        secondLaser.setChecked(sharedPreferences.getBoolean("checkSecondLaser",false));
    }


    private boolean isCheckedSwitch(Switch sw) {
        return sw.isChecked();
    }
    private boolean isCheckedBox(CheckBox checkBox) {
        return checkBox.isChecked();
    }


    private void searchAndConnectBT(int requestConnectDeviceSecure) {
        Intent serverIntent = null;
        serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, requestConnectDeviceSecure);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null) {
            switch (requestCode) {
                case Const.REQUEST_CONNECT_DEVICE_SECURE:
                    if (resultCode == Activity.RESULT_OK) {
                        macBluetooth = data.getExtras().getString(
                                DeviceListActivity.DEVICE_ADDRESS);

                        asyncConnectToDevice(macBluetooth);

                    }
                    break;
                case Const.REQUEST_CONNECT_DEVICE_INSECURE:
                    if (resultCode == Activity.RESULT_OK) {
                        macBluetooth = data.getExtras().getString(
                                DeviceListActivity.DEVICE_ADDRESS);
                        asyncConnectToDevice(macBluetooth);
                    }
                    break;
            }
        }
    }


    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {


                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);        // Получаем кол-во байт и само собщение в байтовый массив "buffer"
                    h.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();     // Отправляем в очередь сообщений Handler
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String message) {
            Log.d(TAG, "...Данные для отправки: " + message + "...");
            message = message + "\r\n";
            byte[] msgBuffer = message.getBytes();
            ;
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {
                Log.d(TAG, "...Ошибка отправки данных: " + e.getMessage() + "...");
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

}
