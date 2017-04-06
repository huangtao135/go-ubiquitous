package com.example.android.sunshine.watchface;


import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.example.android.sunshine.utilities.WearableUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class WeatherListenerService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private final static long TIMEOUT_SECONDS = 30;

    private final static String TAG = WeatherListenerService.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;

    private Node mConnectedNode;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "Created");

        if (null == mGoogleApiClient) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            Log.v(TAG, "GoogleApiClient created");
        }

        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
            Log.v(TAG, "Connecting to GoogleApiClient..");
        }

        //getWeatherData(WearableUtils.WEATHER_WEARABLE_PATH);
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "Destroyed");

        if (null != mGoogleApiClient) {
            if (mGoogleApiClient.isConnected()) {
                mGoogleApiClient.disconnect();
                Log.v(TAG, "GoogleApiClient disconnected");
            }
        }

        super.onDestroy();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected: " + bundle);
        //getWeatherData(WearableUtils.WEATHER_WEARABLE_PATH);
        //Wearable.DataApi.addListener(mGoogleApiClient, WeatherListenerService.this);



    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended: " + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed: " + connectionResult);
    }


    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                if (WearableUtils.WEATHER_WEARABLE_PATH.equalsIgnoreCase(dataEvent.getDataItem()
                        .getUri().getPath())) {
                    //TODO: Just for testing. Put real weather data later.
                    Asset iconAsset = dataMap.getAsset("icon");
                    Bitmap bitmap = loadBitmapFromAsset(iconAsset);

                    //TODO: Must add icon Bitmap
                    sendWeatherUpdateBroadcast(dataMap.getLong("max"), dataMap.getLong("min"));

                    WearableUtils.saveWeatherData(WeatherListenerService.this,
                            dataMap.getLong("max"), dataMap.getLong("min"));

                }
            }
        }
        dataEvents.release();
    }


    // TODO: Not needed?
    // https://medium.com/@manuelvicnt/android-wear-accessing-the-data-layer-api-d64fd55982e3
    private void getWeatherData(final String pathToContent) {
        setConnectedNode();

        Wearable.NodeApi.getLocalNode(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetLocalNodeResult>() {
            @Override
            public void onResult(@NonNull NodeApi.GetLocalNodeResult getLocalNodeResult) {
                Uri uri = new
                        Uri.Builder()
                        .scheme(PutDataRequest.WEAR_URI_SCHEME)
                        .path(pathToContent)
                        .authority(mConnectedNode.getId())
                        .build();

                Wearable.DataApi.getDataItem(mGoogleApiClient, uri).setResultCallback(
                        new ResultCallback<DataApi.DataItemResult>() {
                            @Override
                            public void onResult(DataApi.DataItemResult dataItemResult) {
                                if (dataItemResult.getStatus().isSuccess() &&
                                        dataItemResult.getDataItem() != null) {

                                    DataMap dataMap = DataMapItem.fromDataItem(dataItemResult
                                            .getDataItem()).getDataMap();
                                    if (WearableUtils.WEATHER_WEARABLE_PATH.equalsIgnoreCase
                                            (dataItemResult.getDataItem().getUri().getPath())) {

                                        //TODO: Just for testing. Put real weather data later.
                                        Asset iconAsset = dataMap.getAsset("icon");
                                        Bitmap bitmap = loadBitmapFromAsset(iconAsset);

                                        //TODO: Must add icon Bitmap
                                        sendWeatherUpdateBroadcast(dataMap.getLong("max"),
                                                dataMap.getLong("min"));

                                        WearableUtils.saveWeatherData(WeatherListenerService.this,
                                                dataMap.getLong("max"), dataMap.getLong("min"));

                                    }
                                }
                            }
                        });
            }

        });
    }

    private void setConnectedNode() {
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult nodes) {
                for (Node node : nodes.getNodes()) {
                    mConnectedNode = node;
                }
            }
        });
    }


    //TODO: Must add icon Bitmap
    private void sendWeatherUpdateBroadcast(long high, long low) {
        Intent intent = new Intent();
        intent.setAction(WearableUtils.ACTION_WEATHER_UPDATED);
        intent.putExtra(WearableUtils.EXTRA_HIGH_TEMPERATURE, high);
        intent.putExtra(WearableUtils.EXTRA_LOW_TEMPERATURE, low);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // https://developer.android.com/training/wearables/data-layer/assets.html#ReceiveAsset
    public Bitmap loadBitmapFromAsset(Asset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }

        //TODO: Must not be in the UI thread
        ConnectionResult result =
                mGoogleApiClient.blockingConnect(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!result.isSuccess()) {
            return null;
        }

        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }
}
