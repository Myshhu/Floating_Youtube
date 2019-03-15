package com.example.youtubeapiservice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

class VideosInformation {

    private Context applicationContext;
    private String[] apiKeysArray = {
            com.example.youtubeapiservice.API_KEY.KEY,
            com.example.youtubeapiservice.API_KEY.KEY1};
    private int currentAPIKEY = 0;
    private String apiKey = apiKeysArray[0];
    private JSONObject videoInformationJSONObject;

    VideosInformation(String searchQuery, Context applicationContext) {
        this.applicationContext = applicationContext;
        videoInformationJSONObject = getVideoInformationObject(searchQuery);
    }

    JSONObject getFirstVideoInfo() {
        try {
            return videoInformationJSONObject.getJSONArray("items").
                    getJSONObject(0);
        } catch (Exception e){
            return null;
        }
    }

    private JSONObject getVideoInformationObject(String searchQuery) {
        JSONObject videoInformationObject = null;
        boolean availableSpareAPIKeys = true;

        try {
            while (availableSpareAPIKeys) {
                HttpURLConnection connection = createConnectionWithQuery(searchQuery);
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    videoInformationObject = createJSONObjectFromConnectionInputStream(connection);
                    break;
                } else if (connection.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN) { //FORBIDDEN = API Key limit reached
                    availableSpareAPIKeys = isSpareAPIKeyAvailable();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return videoInformationObject;
    }

    private HttpURLConnection createConnectionWithQuery(String searchQuery) {

        try {
            HttpURLConnection connection;
            URL url = new URL(
                    "https://www.googleapis.com/youtube/v3/search?part=snippet&q=" +
                            searchQuery + "&maxResults=1&type=video&key=" + apiKey
            );
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            return connection;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private JSONObject createJSONObjectFromConnectionInputStream(HttpURLConnection connection) throws Exception {
        InputStream connectionInputStream = connection.getInputStream();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connectionInputStream));
        StringBuilder result = new StringBuilder();
        String line;
        while((line = bufferedReader.readLine()) != null) {
            result.append(line).append("\n");
        }
        return new JSONObject(result.toString());
    }

    private boolean isSpareAPIKeyAvailable() {
        if (isCurrentKeyTheLastAvailableKey()) {
            createToast("API limits exceeded, try again later");
            changeCurrentAPIKEYToFirst();
            return false;
        } else {
            changeCurrentAPIKEYToNext();
            return true;
        }
    }

    private boolean isCurrentKeyTheLastAvailableKey() {
      return currentAPIKEY == apiKeysArray.length - 1;
    }

    private void createToast(String text) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show());
    }

    private void changeCurrentAPIKEYToFirst() {
        currentAPIKEY = 0;
        apiKey = apiKeysArray[currentAPIKEY];
    }

    private void changeCurrentAPIKEYToNext() {
        apiKey = apiKeysArray[++currentAPIKEY];
        Log.d("AppInfo", "Changed key to $currentAPIKEY");
    }
}
