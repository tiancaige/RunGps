package com.bravo.rungps.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.Polyline;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.bravo.rungps.orbit.MsgObservable;
import com.bravo.rungps.orbit.RunConstants;
import com.bravo.rungps.orbit.adapter.LatAdapter;
import com.bravo.rungps.orbit.bean.Information;
import com.bravo.rungps.orbit.bean.PositionBean;
import com.bravo.rungps.orbit.services.WorkService;
import com.bravo.rungps.orbit.ui.MainActPresenter;
import com.bravo.rungps.orbit.ui.MainActView;
import com.bravo.rungps.orbit.ui.base.BaseMVPActivity;
import com.bravo.rungps.orbit.utils.Logger;
import com.bravo.rungps.R;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.util.Date;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;

/*
* 定位
 */
public class OrbitLocationActivity extends BaseMVPActivity<MainActView, MainActPresenter> implements View.OnClickListener, MainActView, Observer {

    protected static final int EMPTY_MSG = 0;
    private MapView mMapView=null;
    private ImageView mIvPosition=null;
    private ListView mLvLocationLats=null;
    private TextView mTvDis=null;
    private TextView mTvCalorie=null;
    private TextView mTvHeartRate=null;
    private TextView mTvHourV=null;
    private TextView mTvTimes=null;
    private TextView mTvV=null;
    private Button mBtnPause=null;
    private Button mBtnStart=null;
    private Button mBtnEnd=null;
    private BaiduMap mBaiduMap=null;
    private SDKReceiver mReceiver=null;

    private boolean isAttch;

    private LatAdapter mListGpsLocationAdapter;

    private LinkedList<LatLng> mPointListWithGps = new LinkedList<>();
    private LinkedList<PositionBean> mMoveGpsDataList = new LinkedList<>();

    private Timer timer1;
    private TimerTask timerTask;
    private Chronometer timer;

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MsgObservable.getInstance().deleteObserver(this);
        isAttch = false;
        mMapView.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_orbitmain);
        MsgObservable.getInstance().addObserver(this);
        isAttch = true;
        initView();
        initMap(savedInstanceState);
        initEvent();
        getCurrentLocation();

//       timer = (Chronometer) findViewById(R.id.tv_times);



    }


    @Override
    protected MainActPresenter createPresenter() {
        return new MainActPresenter(this);
    }

    private void initView() {

        mMapView = (MapView) findViewById(R.id.mapView);

        mIvPosition = (ImageView) findViewById(R.id.iv_position);
        mLvLocationLats = (ListView) findViewById(R.id.lv_location);
        mTvDis = (TextView) findViewById(R.id.tv_distance);
//        mTvCalorie = (TextView) findViewById(R.id.tv_calorie);
//        mTvHeartRate = (TextView) findViewById(R.id.tv_heart_rate);
//        mTvHourV = (TextView) findViewById(R.id.tv_hour_v);
////        mTvTimes = (TextView) findViewById(R.id.tv_times);
        mTvV = (TextView) findViewById(R.id.tv_v);

        mBtnPause = (Button) findViewById(R.id.btn_pause);
        mBtnStart = (Button) findViewById(R.id.btn_start);
        mBtnEnd = (Button) findViewById(R.id.btn_end);

    }

    private void initMap(Bundle savedInstanceState) {

        mMapView.onCreate(this, savedInstanceState);
        // 初始化地图
        mMapView.showZoomControls(false);

        mBaiduMap = mMapView.getMap();

        // 设置缩放级别
        mBaiduMap.setMapStatus(
                MapStatusUpdateFactory.newMapStatus(new MapStatus.Builder().zoom(19).build()));
        // 开启定位图层
        mBaiduMap.setMyLocationEnabled(true);
        // 开启交通图层
        mBaiduMap.setTrafficEnabled(false);
        // 开启热力图层
        mBaiduMap.setBaiduHeatMapEnabled(false);

        IntentFilter iFilter = new IntentFilter();
        iFilter.addAction(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR);
        iFilter.addAction(SDKInitializer.SDK_BROADCAST_ACTION_STRING_NETWORK_ERROR);
        mReceiver = new SDKReceiver();
        registerReceiver(mReceiver, iFilter);

    }


    private boolean isStop = true;

    private void initEvent() {

        mIvPosition.setOnClickListener(this);
        mBtnStart.setOnClickListener(this);
        mBtnPause.setOnClickListener(this);
        mBtnEnd.setOnClickListener(this);

        mListGpsLocationAdapter = new LatAdapter(this, mMoveGpsDataList);
        mLvLocationLats.setAdapter(mListGpsLocationAdapter);
    }

    @SuppressLint("CheckResult")
    private void getCurrentLocation() {
        RxPermissions rxPermissions = new RxPermissions(this);
        rxPermissions.request(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                .subscribe(granted -> {
                    if (granted) {
                        //有权限了。
                        startService(new Intent(OrbitLocationActivity.this, WorkService.class));
                    } else {
                        //没有权限。
                        showToast("没有权限");
                    }
                });
    }

    /**
     * 接收service的信息。
     *
     * @param o
     * @param arg
     */
    @Override
    public void update(Observable o, Object arg) {
        Logger.d("lilee", "update once");
        if (o instanceof MsgObservable) {
            if (arg instanceof Message) {
                Message msg = (Message) arg;
                switch (msg.what) {
                    case RunConstants.FIRST_LOCATION_MSG:
                        if (msg.obj instanceof Location && msg.obj != null) {
                            runOnUiThread(() -> firstGetLocation((Location) msg.obj));
                        }
                        break;
                    case RunConstants.RUN_FIRST_LOCATION_MSG:
                        if (msg.obj instanceof Location && msg.obj != null) {
                            runOnUiThread(() -> runGetFirstLocation((Location) msg.obj));
                        }
                        break;
                    case RunConstants.RUN_LOCATION_MSG:
                        if (msg.obj instanceof Information && msg.obj != null) {
                            runOnUiThread(() -> updateLocation((Information) msg.obj));
                        }
                        break;
                }
            }
        }
    }

    private void updateLocation(Information info) {
        //位置变化
        mPointListWithGps.clear();
        mPointListWithGps.addAll(info.mLocationList);
        mMoveGpsDataList.add(info.mPositionBean);
        mListGpsLocationAdapter.notifyDataSetChanged();
        moveMap(mMoveGpsDataList);
        updateListenerView();
    }


    private void runGetFirstLocation(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        LatLng latLng = new LatLng(latitude, longitude);
        latLng = mActPersenter.gpsToBaidu(latLng);
        mPointListWithGps.add(latLng);

        PositionBean positionBean =
                new PositionBean().setLatlng(latLng).setCurrentTime(System.currentTimeMillis())
                        .setDistance(0).setVelocity(0).setPreGapTime(0).setGpsSpeed(speed);

        mMoveGpsDataList.add(positionBean);

        if (latLng != null) {
            MyLocationData locData = new MyLocationData.Builder().accuracy(40)
                    // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(100).latitude(latLng.latitude).longitude(latLng.longitude)
                    .build();
            mBaiduMap.setMyLocationData(locData);
        }

        // 将地图移动到LatLng点。
        moveMap(mMoveGpsDataList);
        updateListenerView();
    }

    private void firstGetLocation(Location location) {
        mPointListWithGps.clear();
        speed = location.getSpeed();
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        LatLng latLng = new LatLng(latitude, longitude);
        latLng = mActPersenter.gpsToBaidu(latLng);
        PositionBean positionBean =
                new PositionBean().setLatlng(latLng).setCurrentTime(System.currentTimeMillis())
                        .setDistance(0).setVelocity(0).setPreGapTime(0).setGpsSpeed(speed);
        mMoveGpsDataList.add(positionBean);
        if (latLng != null) {
            MyLocationData locData = new MyLocationData.Builder().accuracy(40)
                    // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(100).latitude(latLng.latitude).longitude(latLng.longitude)
                    .build();
            mBaiduMap.setMyLocationData(locData);
        }
        // 将地图移动到LatLng点。
        moveMap(mMoveGpsDataList);
        updateListenerView();
    }

    // 将地图移动到list的最后一个点。
    private void moveMap(LinkedList<PositionBean> list) {
        MapStatusUpdate mMapStatusUpdate = mActPersenter.getMapStatusUpdate(list);
        if (mMapStatusUpdate != null) {
            mBaiduMap.setMapStatus(mMapStatusUpdate);
        } else {
            showToast("暂未拿到当前位置");
        }
    }

    private float mTotalDistance;
    private long mTotalTimes;
    private float speed;

    private void updateListenerView() {
        setDistance();
        if (mMoveGpsDataList != null && mMoveGpsDataList.size() >= 1) {
            PositionBean positionBean = mMoveGpsDataList.getLast();
            mTvDis.setText("距离 ： " + mTotalDistance);

            mTvV.setText("配速 ： " + positionBean.velocity/10+ "m/s");
//            mTvV.setText("速度 ： " + mTotalDistance /mTotalTimes+ "m/s");
//            mTvTimes.setText("时间 ： " + getFormatTime(mTotalTimes));
            if (mPointListWithGps != null && mPointListWithGps.size() > 1) {
                mBaiduMap.clear();// 先将地图上的点清除。
                // 在地图上画线。
                OverlayOptions polylineOption = new PolylineOptions().points(mPointListWithGps)
                        .width(10).color(Color.RED);
                Polyline mVirtureRoad = (Polyline) mBaiduMap.addOverlay(polylineOption);
                // 在地图上画起始点。
                OverlayOptions markerOptions;
                markerOptions = new MarkerOptions().flat(true).anchor(0.5f, 0.5f)
                        .icon(BitmapDescriptorFactory.fromResource(R.mipmap.icon_positioning_big))
                        .position(mPointListWithGps.get(0));
                Marker mMoveMarker = (Marker) mBaiduMap.addOverlay(markerOptions);
            }
            // 设置当前位置的蓝色的点。
            LatLng latLng = positionBean.latlng;
            if (latLng != null) {
                MyLocationData locData = new MyLocationData.Builder().accuracy(40)
                        // 此处设置开发者获取到的方向信息，顺时针0-360
                        .direction(100).latitude(latLng.latitude).longitude(latLng.longitude)
                        .build();
                mBaiduMap.setMyLocationData(locData);
            }
        }
    }


    private void setDistance() {
        if (mPointListWithGps == null) {
            mTotalDistance = 0;
            return;
        }

        if (mPointListWithGps.size() < 2) {
            mTotalDistance = 0;
            return;
        }

        float[] result = new float[3];
        int size = mPointListWithGps.size();
        LatLng start = mPointListWithGps.get(size - 2);
        LatLng end = mPointListWithGps.get(size - 1);
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude,
                result);
        mTotalDistance += result[0];
    }

    long prelongTim = 0;//定义上一次单击的时间
    @Override
    public void onClick(View v) {
        // 点击事件
        switch (v.getId()) {
            case R.id.iv_position:
                moveMap(mMoveGpsDataList);
                break;
            case R.id.btn_start:


                isStop = !isStop;
                if (isStop) {
                    mBtnStart.setText("Start");

                }
                else {
                    mBtnStart.setText("Stop");
//                    long curTime = (new Date()).getTime();//本次单击的时间    状态：跑步中
//                    mTvTimes.setText("跑步时间 ： " +  System.currentTimeMillis());
//
//                    timer.setBase(SystemClock.elapsedRealtime());//计时器清零
//                    int hour = (int) ((SystemClock.elapsedRealtime() - timer.getBase()) / 1000 / 60);
//                    timer.setFormat("0"+String.valueOf(hour)+":%s");
//                    timer.start();

                }
                WorkService.isRunStop = isStop;
                break;
            case R.id.btn_pause:
                isStop = true;
                break;
            case R.id.btn_end:
                isStop = true;

                timer.stop();

                break;

            default:
                break;
        }

    }
}
