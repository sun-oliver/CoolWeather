package com.example.oliver.coolweather.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.oliver.coolweather.R;
import com.example.oliver.coolweather.db.CoolWeatherDB;
import com.example.oliver.coolweather.model.City;
import com.example.oliver.coolweather.model.County;
import com.example.oliver.coolweather.model.Province;
import com.example.oliver.coolweather.util.HttpCallbackListener;
import com.example.oliver.coolweather.util.HttpUtil;
import com.example.oliver.coolweather.util.LogUtils;
import com.example.oliver.coolweather.util.Utility;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by Oliver on 2016/6/16.
 */
public class ChooseAreaActivity extends Activity {

    public static final String TAG = "ChooseAreaActivity";
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;

    private ProgressDialog progressDialog;
    private TextView titleText;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private CoolWeatherDB coolWeatherDB;
    private List<String> dataList = new ArrayList<String>();

    /**
     * 省列表
     */
    private List<Province> provinceList;
    /**
     * 市列表
     */
    private List<City> cityList;
    /**
     * 县列表
     */
    private List<County> countyList;
    /**
     * 选中的省份
     */
    private Province selectedProvince;
    /**
     * 选中的城市
     */
    private City selectedCity;
    /**
     * 当前选中的级别
     */
    private int currentLevel;

    /**
     * 是否从WeatherActivity中跳转过来
     */
    private boolean isFromWeatherActivity;

//    不使用runOnUIThread 使用handler + Message
//    private Handler mHandler = new Handler(){
//        @Override
//        public void handleMessage(Message msg) {
//            switch (msg.what){
//                case 1:
//                    queryProvinces();
//                    break;
//                case 2:
//                    queryCities();
//                    break;
//                case 3:
//                    queryCounties();
//                    break;
//            }
//        }
//    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isFromWeatherActivity = getIntent().getBooleanExtra("from_weather_activity",false);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //已选择了城市且不是从WeatherActivity跳转过来，才会直接跳转到weatherActivity
        if (prefs.getBoolean("city_selected",false) && !isFromWeatherActivity){
            Intent intent = new Intent(ChooseAreaActivity.this,WeatherActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.choose_area);
        //获取到一些控件实例
        listView = (ListView) findViewById(R.id.list_view);
        titleText = (TextView) findViewById(R.id.title_text);
        //初始化ArrayAdapter
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, dataList);
        listView.setAdapter(adapter);
        //获取coolWeatherDB的实例
        coolWeatherDB = CoolWeatherDB.getInstance(this);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel == LEVEL_PROVINCE) {
                    selectedProvince = provinceList.get(position);
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    selectedCity = cityList.get(position);
                    queryCounties();
                } else if (currentLevel == LEVEL_COUNTY){
                    String countyCode = countyList.get(position).getCountyCode();
                    Intent intent = new Intent(ChooseAreaActivity.this,WeatherActivity.class);
                    intent.putExtra("county_code",countyCode);
                    startActivity(intent);
                    finish();
                }
            }
        });
        queryProvinces();//加载省级数据

    }

    /**
     * 查询全国所有的省，优先从数据库中查询，如果没有查询到再去服务器上查询
     */
    private void queryProvinces() {
        provinceList = coolWeatherDB.loadProvince();
        if (provinceList.size() > 0) {
            LogUtils.e(TAG,"queryProvinces>>>coolWeatherDB");
            dataList.clear();
            for (Province province : provinceList) {
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleText.setText("中国");
            currentLevel = LEVEL_PROVINCE;
        } else {
            LogUtils.e(TAG,"queryProvinces>>>queryFromServer");
            queryFromServer(null, "province");
        }
    }

    /**
     * 查询选中省内的所有的市，优先从数据库中查询，如果没有查询到再去服务器上查询
     */
    private void queryCities() {
        cityList = coolWeatherDB.loadCities(selectedProvince.getId());
        if (cityList.size() > 0) {
            dataList.clear();
            for (City city : cityList) {
                dataList.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleText.setText(selectedProvince.getProvinceName());
            currentLevel = LEVEL_CITY;
        } else {
            queryFromServer(selectedProvince.getProvinceCode(), "city");
        }
    }

    /**
     * 查询选中省内的所有的县，优先从数据库中查询，如果没有查询到再去服务器上查询
     */
    private void queryCounties() {
        countyList = coolWeatherDB.loadCounties(selectedProvince.getId());
        if (countyList.size() > 0) {
            dataList.clear();
            for (County county : countyList) {
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleText.setText(selectedCity.getCityName());
            currentLevel = LEVEL_COUNTY;
        } else {
            queryFromServer(selectedCity.getCityCode(), "county");
        }
    }

    /**
     * 根据穿入的代号和类型从服务器上查询省市县数据
     */
    private void queryFromServer(final String code, final String type) {
        String address;
        if (!TextUtils.isEmpty(code)) {
            LogUtils.e(TAG,"queryFromServer >>> not null");
            address = "http://www.weather.com.cn/data/list3/city" + code + ".xml";

        } else {
            Log.e(TAG,"queryFromServer >>> null");
            address = "http://www.weather.com.cn/data/list3/city.xml";
        }

        showProgressDialog();
        LogUtils.e(TAG,"code = " + code);
        //调用HttpUtil的sendHttpRequest（）方法向服务器发送数据
        //HttpUtil.sendHttpRequest(address,mListener);

        //调用HttpUtil的sendHttpRequest（）方法向服务器发送数据
        HttpUtil.sendHttpRequest(address, new HttpCallbackListener() {
            //调用Utility的handleProvinceResponse()来解析和处理服务器返回的数据，并存储到数据库中
            //然后在解析和处理完成后 在此调用queryProvinces()方法来家在加载生机数据
            //由于queryProvinces()方法牵扯到UI操作 所以借助了runOnUiThread（）方法来实现从子线程切换到主线程
            @Override
            public void onFinish(String response) {
                boolean result = false;
                if ("province".equals(type)) {
                    result = Utility.handleProvinceResponse(coolWeatherDB, response);
                } else if ("city".equals(type)) {
                    result = Utility.handleCitiesResponse(coolWeatherDB, response, selectedProvince.getId());
                } else if ("county".equals(type)) {
                    result = Utility.handleCountiesResponse(coolWeatherDB, response, selectedCity.getId());
                }
                if (result) {
                    //通过runOnUIThread（）方法回到主线程处理逻辑
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closProgressDialog();
                            if ("province".equals(type)) {
                                queryProvinces();
                            } else if ("city".equals(type)) {
                                queryCities();
                            } else if ("county".equals(type)) {
                                queryCounties();
                            }

                        }
                    });
//                    不使用runOnUIThread 使用handler + Message
//                    if ("province".equals(type)) {
                          //Message不要用new方法创建，要使用handler.obtainMessage方法创建，
                          // 因为这里有做对象池的优化，防止大量消息产生的内存碎片。
//                        Message msg = Message.obtain();
//                        msg.what =1;
//                        mHandler.sendMessage(msg);
//                    } else if ("city".equals(type)) {
//                        Message msg = Message.obtain();
//                        msg.what =2;
//                        mHandler.sendMessage(msg);
//                    } else if ("county".equals(type)) {
//                        Message msg = Message.obtain();
//                        msg.what =3;
//                        mHandler.sendMessage(msg);
//                    }

                }

            }

            @Override
            public void onError(Exception e) {
                //通过runOnUIThread（）方法回到主线程处理逻辑
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closProgressDialog();
                        Toast.makeText(ChooseAreaActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

    }

//    private HttpCallbackListener mListener = new HttpCallbackListener() {
//        @Override
//        public void onFinish(String response) {
//
//        }
//
//        @Override
//        public void onError(Exception e) {
//
//        }
//    };

    /**
     * 显示进度对话框
     */
    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }

    /**
     * 关闭进度对话框
     */
    private void closProgressDialog(){
        if (progressDialog != null){
            progressDialog.dismiss();
        }
    }

    /**
     * 捕获Back按键，根据当前的级别来判断，此时应该返回市列表，省列表还会还是直接退出
     */
    @Override
    public void onBackPressed() {
        if (currentLevel == LEVEL_COUNTY){
            queryCities();
        }else if (currentLevel == LEVEL_CITY){
            queryCounties();
        } else {
            if (isFromWeatherActivity){
                Intent intent = new Intent(ChooseAreaActivity.this,WeatherActivity.class);
                startActivity(intent);
            }
            finish();
        }

    }

    /**
     * 利用反射
     */
//    public void sssss(){
//        ChooseAreaActivity ss = new ChooseAreaActivity();
//        try {
//            Class sqw = Class.forName("com.example.oliver.coolweather.activity.ChooseAreaActivity");//进行类加载，返回该类的Class对象
//            Method[] method=sqw.getDeclaredMethods();//利用得到的Class对象的自审，返回方法对象集合
//            Field[] field=sqw.getDeclaredFields();//利用得到的Class对象的自审，返回属性对象集合
//            for(Method me:method){//遍历该类方法的集合
//                LogUtils.e(TAG,"方法有 = "+ me.toString());//打印方法信息
//            }
//            for(Field me:field){ //遍历该类属性的集合
//                LogUtils.e(TAG, "属性有 = "+me.toString());//打印属性信息
//            }
//
//            LogUtils.e(TAG,"method =" + method);
//            LogUtils.e(TAG,"field = " + field);
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        }
//    }
}

